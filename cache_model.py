import argparse
import json
import os
import random
from dataclasses import dataclass
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as F
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.utils.class_weight import compute_class_weight
from sklearn.inspection import permutation_importance


@dataclass
class Config:
    data_path: str = ""
    model_dir: str = "artifacts"
    seed: int = 42
    test_size: float = 0.2
    val_size: float = 0.2
    batch_size: int = 32
    max_epochs: int = 50
    patience: int = 7
    lr: float = 1e-3
    hidden_sizes: Tuple[int, int] = (128, 64)
    dropout: float = 0.1
    embedding_dims: Dict[str, int] = None
    numeric_features: Tuple[str, ...] = (
        "avg_size_bytes",
        "p95_size_bytes",
        "read_qps",
        "write_qps",
        "update_interval_sec",
        "fanout_services",
        "staleness_tolerance_sec",
        "recompute_cost_ms",
        "db_latency_ms",
        "peakiness",
        "is_user_specific",
        "is_shared_globally",
    )
    categorical_features: Tuple[str, ...] = (
        "object_type",
        "consistency_required",
    )
    target_placement: str = "y_cache_placement"
    target_volatility: str = "y_volatility"
    export_onnx: bool = False


CONFIG = Config(
    embedding_dims={
        "object_type": 8,
        "consistency_required": 4,
    }
)


CACHE_PLACEMENT_CLASSES = ["EMBEDDED", "SIDECAR", "MULTI_LEVEL"]
VOLATILITY_CLASSES = ["STATIC", "MEDIUM", "DYNAMIC"]


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def validate_columns(df: pd.DataFrame, config: Config) -> None:
    expected = set(config.numeric_features + config.categorical_features)
    targets = {config.target_placement, config.target_volatility}
    missing = (expected | targets) - set(df.columns)
    if missing:
        raise ValueError(
            f"Missing required columns: {sorted(missing)}. "
            "Please provide all expected feature and target columns."
        )


def generate_synthetic_data(n_rows: int = 300, seed: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    object_types = [
        "book_text",
        "cover_image",
        "metadata",
        "rating",
        "user_state",
        "reco",
    ]
    consistency_levels = ["strong", "eventual", "weak"]
    rows = []

    for _ in range(n_rows):
        object_type = rng.choice(object_types)
        is_user_specific = 1 if object_type in {"user_state", "reco"} else 0
        is_shared_globally = 1 if object_type in {"book_text", "cover_image", "metadata"} else 0

        base_size = {
            "book_text": 600_000,
            "cover_image": 300_000,
            "metadata": 20_000,
            "rating": 2_000,
            "user_state": 5_000,
            "reco": 25_000,
        }[object_type]
        avg_size_bytes = rng.normal(base_size, base_size * 0.1)
        p95_size_bytes = avg_size_bytes * rng.uniform(1.1, 1.6)

        read_qps = rng.uniform(50, 600) if is_shared_globally else rng.uniform(5, 120)
        write_qps = rng.uniform(0.01, 1.0) if object_type in {"book_text", "cover_image"} else rng.uniform(0.5, 15)
        update_interval_sec = rng.uniform(10_000, 200_000) if object_type in {"book_text", "cover_image"} else rng.uniform(30, 8_000)
        fanout_services = rng.integers(1, 10) if is_user_specific else rng.integers(5, 50)
        staleness_tolerance_sec = rng.uniform(30, 3_600) if is_user_specific else rng.uniform(300, 86_400)
        recompute_cost_ms = rng.uniform(5, 150) if object_type == "metadata" else rng.uniform(20, 800)
        db_latency_ms = rng.uniform(5, 80) if is_user_specific else rng.uniform(20, 200)
        peakiness = rng.uniform(0.1, 1.0) if object_type in {"reco", "rating"} else rng.uniform(0.05, 0.6)

        if object_type in {"book_text", "cover_image"}:
            placement = "SIDECAR"
            volatility = "STATIC"
            consistency = "eventual"
        elif object_type in {"metadata", "rating"}:
            placement = rng.choice(["SIDECAR", "MULTI_LEVEL"], p=[0.4, 0.6])
            volatility = "MEDIUM"
            consistency = "eventual"
        else:
            placement = rng.choice(["EMBEDDED", "MULTI_LEVEL"], p=[0.5, 0.5])
            volatility = "DYNAMIC"
            consistency = "strong" if object_type == "user_state" else "eventual"

        rows.append(
            {
                "object_type": object_type,
                "avg_size_bytes": max(1, avg_size_bytes),
                "p95_size_bytes": max(1, p95_size_bytes),
                "read_qps": read_qps,
                "write_qps": write_qps,
                "update_interval_sec": update_interval_sec,
                "fanout_services": fanout_services,
                "staleness_tolerance_sec": staleness_tolerance_sec,
                "recompute_cost_ms": recompute_cost_ms,
                "db_latency_ms": db_latency_ms,
                "is_user_specific": is_user_specific,
                "is_shared_globally": is_shared_globally,
                "consistency_required": consistency,
                "peakiness": peakiness,
                "y_cache_placement": placement,
                "y_volatility": volatility,
            }
        )

    return pd.DataFrame(rows)


class TabularDataset(torch.utils.data.Dataset):
    def __init__(self, numeric: np.ndarray, categorical: np.ndarray, y_place: np.ndarray, y_vol: np.ndarray):
        self.numeric = torch.tensor(numeric, dtype=torch.float32)
        self.categorical = torch.tensor(categorical, dtype=torch.long)
        self.y_place = torch.tensor(y_place, dtype=torch.long)
        self.y_vol = torch.tensor(y_vol, dtype=torch.long)

    def __len__(self) -> int:
        return len(self.numeric)

    def __getitem__(self, idx: int):
        return self.numeric[idx], self.categorical[idx], self.y_place[idx], self.y_vol[idx]


class TabularNN(nn.Module):
    def __init__(
        self,
        num_numeric: int,
        cat_cardinalities: List[int],
        cat_embedding_dims: List[int],
        hidden_sizes: Tuple[int, int],
        dropout: float,
        num_classes_place: int,
        num_classes_vol: int,
    ):
        super().__init__()
        self.embeddings = nn.ModuleList(
            [nn.Embedding(card, dim) for card, dim in zip(cat_cardinalities, cat_embedding_dims)]
        )
        total_embed_dim = sum(cat_embedding_dims)
        input_dim = num_numeric + total_embed_dim

        layers = []
        prev = input_dim
        for size in hidden_sizes:
            layers.append(nn.Linear(prev, size))
            layers.append(nn.ReLU())
            layers.append(nn.Dropout(dropout))
            prev = size
        self.backbone = nn.Sequential(*layers)
        self.head_place = nn.Linear(prev, num_classes_place)
        self.head_vol = nn.Linear(prev, num_classes_vol)

    def forward(self, numeric: torch.Tensor, categorical: torch.Tensor):
        embeds = [emb(categorical[:, i]) for i, emb in enumerate(self.embeddings)]
        concat = torch.cat([numeric] + embeds, dim=1)
        shared = self.backbone(concat)
        return self.head_place(shared), self.head_vol(shared)


class Preprocessor:
    def __init__(self, config: Config):
        self.config = config
        self.numeric_imputer = SimpleImputer(strategy="median")
        self.scaler = StandardScaler()
        self.cat_vocabs: Dict[str, Dict[str, int]] = {}

    def fit(self, df: pd.DataFrame) -> None:
        numeric = df[list(self.config.numeric_features)]
        self.numeric_imputer.fit(numeric)
        self.scaler.fit(self.numeric_imputer.transform(numeric))

        for col in self.config.categorical_features:
            values = df[col].fillna("UNK").astype(str)
            uniques = sorted(values.unique().tolist())
            if "UNK" not in uniques:
                uniques.append("UNK")
            vocab = {val: idx for idx, val in enumerate(uniques)}
            self.cat_vocabs[col] = vocab

    def transform(self, df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        numeric = df[list(self.config.numeric_features)]
        numeric = self.numeric_imputer.transform(numeric)
        numeric = self.scaler.transform(numeric)

        cat_arrays = []
        for col in self.config.categorical_features:
            values = df[col].fillna("UNK").astype(str)
            vocab = self.cat_vocabs[col]
            indices = [vocab.get(val, vocab["UNK"]) for val in values]
            cat_arrays.append(np.array(indices))
        categorical = np.stack(cat_arrays, axis=1)
        return numeric, categorical


def compute_class_weights(labels: np.ndarray, classes: List[str]) -> torch.Tensor:
    class_indices = np.arange(len(classes))
    weights = compute_class_weight(class_weight="balanced", classes=class_indices, y=labels)
    return torch.tensor(weights, dtype=torch.float32)


def train_model(
    model: nn.Module,
    train_loader: torch.utils.data.DataLoader,
    val_loader: torch.utils.data.DataLoader,
    weights_place: torch.Tensor,
    weights_vol: torch.Tensor,
    config: Config,
    device: torch.device,
) -> nn.Module:
    optimizer = torch.optim.Adam(model.parameters(), lr=config.lr)
    best_score = -np.inf
    patience_counter = 0

    for epoch in range(config.max_epochs):
        model.train()
        for numeric, categorical, y_place, y_vol in train_loader:
            numeric = numeric.to(device)
            categorical = categorical.to(device)
            y_place = y_place.to(device)
            y_vol = y_vol.to(device)

            logits_place, logits_vol = model(numeric, categorical)
            loss_place = F.cross_entropy(logits_place, y_place, weight=weights_place.to(device))
            loss_vol = F.cross_entropy(logits_vol, y_vol, weight=weights_vol.to(device))
            loss = loss_place + loss_vol

            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

        metrics = evaluate_model(model, val_loader, device)
        score = (metrics["placement_macro_f1"] + metrics["volatility_macro_f1"]) / 2
        if score > best_score:
            best_score = score
            patience_counter = 0
            best_state = model.state_dict()
        else:
            patience_counter += 1
            if patience_counter >= config.patience:
                break

    model.load_state_dict(best_state)
    return model


def evaluate_model(model: nn.Module, data_loader: torch.utils.data.DataLoader, device: torch.device) -> Dict[str, float]:
    model.eval()
    all_place = []
    all_vol = []
    all_place_pred = []
    all_vol_pred = []

    with torch.no_grad():
        for numeric, categorical, y_place, y_vol in data_loader:
            numeric = numeric.to(device)
            categorical = categorical.to(device)
            logits_place, logits_vol = model(numeric, categorical)
            all_place.extend(y_place.numpy())
            all_vol.extend(y_vol.numpy())
            all_place_pred.extend(torch.argmax(logits_place, dim=1).cpu().numpy())
            all_vol_pred.extend(torch.argmax(logits_vol, dim=1).cpu().numpy())

    place_acc = accuracy_score(all_place, all_place_pred)
    vol_acc = accuracy_score(all_vol, all_vol_pred)
    place_f1 = f1_score(all_place, all_place_pred, average="macro")
    vol_f1 = f1_score(all_vol, all_vol_pred, average="macro")

    return {
        "placement_accuracy": place_acc,
        "volatility_accuracy": vol_acc,
        "placement_macro_f1": place_f1,
        "volatility_macro_f1": vol_f1,
        "placement_confusion": confusion_matrix(all_place, all_place_pred).tolist(),
        "volatility_confusion": confusion_matrix(all_vol, all_vol_pred).tolist(),
    }


def build_recommendation(cache_placement: str, volatility: str) -> Dict[str, str]:
    mapping = {
        ("EMBEDDED", "STATIC"): {
            "ttlSec": 86_400,
            "consistency": "eventual",
            "writeStrategy": "cache-aside",
            "eviction": "LRU",
        },
        ("EMBEDDED", "MEDIUM"): {
            "ttlSec": 3_600,
            "consistency": "eventual",
            "writeStrategy": "write-through",
            "eviction": "LRU",
        },
        ("EMBEDDED", "DYNAMIC"): {
            "ttlSec": 120,
            "consistency": "strong",
            "writeStrategy": "write-through",
            "eviction": "LFU",
        },
        ("SIDECAR", "STATIC"): {
            "ttlSec": 604_800,
            "consistency": "eventual",
            "writeStrategy": "cache-aside",
            "eviction": "LFU",
        },
        ("SIDECAR", "MEDIUM"): {
            "ttlSec": 7_200,
            "consistency": "eventual",
            "writeStrategy": "write-behind",
            "eviction": "LRU",
        },
        ("SIDECAR", "DYNAMIC"): {
            "ttlSec": 300,
            "consistency": "eventual",
            "writeStrategy": "write-through",
            "eviction": "LFU",
        },
        ("MULTI_LEVEL", "STATIC"): {
            "ttlSec": 86_400,
            "consistency": "eventual",
            "writeStrategy": "cache-aside",
            "eviction": "LRU",
        },
        ("MULTI_LEVEL", "MEDIUM"): {
            "ttlSec": 3_600,
            "consistency": "eventual",
            "writeStrategy": "write-behind",
            "eviction": "LRU",
        },
        ("MULTI_LEVEL", "DYNAMIC"): {
            "ttlSec": 180,
            "consistency": "strong",
            "writeStrategy": "write-through",
            "eviction": "LFU",
        },
    }
    return mapping[(cache_placement, volatility)]


def predict_batch(
    model: nn.Module,
    preprocessor: Preprocessor,
    df: pd.DataFrame,
    device: torch.device,
) -> List[Dict[str, object]]:
    model.eval()
    numeric, categorical = preprocessor.transform(df)
    numeric = torch.tensor(numeric, dtype=torch.float32).to(device)
    categorical = torch.tensor(categorical, dtype=torch.long).to(device)

    with torch.no_grad():
        logits_place, logits_vol = model(numeric, categorical)
        probs_place = F.softmax(logits_place, dim=1).cpu().numpy()
        probs_vol = F.softmax(logits_vol, dim=1).cpu().numpy()

    results = []
    for idx in range(len(df)):
        place_idx = int(np.argmax(probs_place[idx]))
        vol_idx = int(np.argmax(probs_vol[idx]))
        placement = CACHE_PLACEMENT_CLASSES[place_idx]
        volatility = VOLATILITY_CLASSES[vol_idx]
        results.append(
            {
                "cachePlacement": placement,
                "volatilityClass": volatility,
                "recommendedPolicy": build_recommendation(placement, volatility),
                "confidence": {
                    "placement": float(np.max(probs_place[idx])),
                    "volatility": float(np.max(probs_vol[idx])),
                },
            }
        )
    return results


def predict_row(model: nn.Module, preprocessor: Preprocessor, row: Dict[str, object], device: torch.device) -> Dict[str, object]:
    df = pd.DataFrame([row])
    return predict_batch(model, preprocessor, df, device)[0]


def train_baseline(
    df_train: pd.DataFrame,
    df_val: pd.DataFrame,
    config: Config,
) -> Dict[str, object]:
    feature_cols = list(config.numeric_features + config.categorical_features)

    preprocessor = ColumnTransformer(
        transformers=[
            (
                "num",
                StandardScaler(),
                config.numeric_features,
            ),
            (
                "cat",
                OneHotEncoder(handle_unknown="ignore"),
                config.categorical_features,
            ),
        ]
    )

    X_train = df_train[feature_cols]
    X_val = df_val[feature_cols]

    X_train_transformed = preprocessor.fit_transform(X_train)
    X_val_transformed = preprocessor.transform(X_val)

    y_place = df_train[config.target_placement].values
    y_vol = df_train[config.target_volatility].values

    place_model = LogisticRegression(max_iter=500, class_weight="balanced")
    vol_model = LogisticRegression(max_iter=500, class_weight="balanced")

    place_model.fit(X_train_transformed, y_place)
    vol_model.fit(X_train_transformed, y_vol)

    place_pred = place_model.predict(X_val_transformed)
    vol_pred = vol_model.predict(X_val_transformed)

    return {
        "placement_accuracy": accuracy_score(df_val[config.target_placement], place_pred),
        "placement_macro_f1": f1_score(df_val[config.target_placement], place_pred, average="macro"),
        "volatility_accuracy": accuracy_score(df_val[config.target_volatility], vol_pred),
        "volatility_macro_f1": f1_score(df_val[config.target_volatility], vol_pred, average="macro"),
        "placement_confusion": confusion_matrix(df_val[config.target_placement], place_pred).tolist(),
        "volatility_confusion": confusion_matrix(df_val[config.target_volatility], vol_pred).tolist(),
        "models": (place_model, vol_model),
        "preprocessor": preprocessor,
    }


def baseline_permutation_importance(
    baseline: Dict[str, object],
    df_sample: pd.DataFrame,
    config: Config,
) -> Dict[str, List[Tuple[str, float]]]:
    feature_cols = list(config.numeric_features + config.categorical_features)
    X_sample = df_sample[feature_cols]
    place_model, vol_model = baseline["models"]
    preprocessor = baseline["preprocessor"]
    X_transformed = preprocessor.transform(X_sample)

    place_importance = permutation_importance(place_model, X_transformed, df_sample[config.target_placement])
    vol_importance = permutation_importance(vol_model, X_transformed, df_sample[config.target_volatility])

    place_scores = sorted(
        zip(feature_cols, place_importance.importances_mean),
        key=lambda x: x[1],
        reverse=True,
    )
    vol_scores = sorted(
        zip(feature_cols, vol_importance.importances_mean),
        key=lambda x: x[1],
        reverse=True,
    )

    return {
        "placement": place_scores,
        "volatility": vol_scores,
    }


def nn_feature_ablation(
    model: nn.Module,
    preprocessor: Preprocessor,
    df_sample: pd.DataFrame,
    config: Config,
    device: torch.device,
) -> Dict[str, List[Tuple[str, float]]]:
    model.eval()
    numeric, categorical = preprocessor.transform(df_sample)
    base_numeric = torch.tensor(numeric, dtype=torch.float32).to(device)
    base_categorical = torch.tensor(categorical, dtype=torch.long).to(device)

    with torch.no_grad():
        logits_place, logits_vol = model(base_numeric, base_categorical)
        base_place = torch.softmax(logits_place, dim=1).mean(dim=0)
        base_vol = torch.softmax(logits_vol, dim=1).mean(dim=0)

    ablation_scores = {}
    for idx, feature in enumerate(config.numeric_features):
        numeric_ablated = base_numeric.clone()
        numeric_ablated[:, idx] = 0
        with torch.no_grad():
            logits_place, logits_vol = model(numeric_ablated, base_categorical)
            place = torch.softmax(logits_place, dim=1).mean(dim=0)
            vol = torch.softmax(logits_vol, dim=1).mean(dim=0)
        ablation_scores[feature] = float((base_place - place).abs().mean() + (base_vol - vol).abs().mean())

    for idx, feature in enumerate(config.categorical_features):
        cat_ablated = base_categorical.clone()
        cat_ablated[:, idx] = 0
        with torch.no_grad():
            logits_place, logits_vol = model(base_numeric, cat_ablated)
            place = torch.softmax(logits_place, dim=1).mean(dim=0)
            vol = torch.softmax(logits_vol, dim=1).mean(dim=0)
        ablation_scores[feature] = float((base_place - place).abs().mean() + (base_vol - vol).abs().mean())

    sorted_scores = sorted(ablation_scores.items(), key=lambda x: x[1], reverse=True)
    return {"combined": sorted_scores}


def serialize_artifacts(model: nn.Module, preprocessor: Preprocessor, config: Config) -> None:
    os.makedirs(config.model_dir, exist_ok=True)
    torch.save(
        {
            "model_state": model.state_dict(),
            "config": config.__dict__,
            "cat_vocabs": preprocessor.cat_vocabs,
            "numeric_imputer": preprocessor.numeric_imputer,
            "scaler": preprocessor.scaler,
        },
        os.path.join(config.model_dir, "tabular_model.pt"),
    )


def export_to_onnx(model: nn.Module, num_numeric: int, num_categorical: int, config: Config) -> None:
    os.makedirs(config.model_dir, exist_ok=True)
    dummy_numeric = torch.zeros(1, num_numeric, dtype=torch.float32)
    dummy_categorical = torch.zeros(1, num_categorical, dtype=torch.long)
    torch.onnx.export(
        model,
        (dummy_numeric, dummy_categorical),
        os.path.join(config.model_dir, "tabular_model.onnx"),
        input_names=["numeric", "categorical"],
        output_names=["placement", "volatility"],
        opset_version=13,
    )


def load_data(config: Config) -> pd.DataFrame:
    if config.data_path:
        if not os.path.exists(config.data_path):
            raise FileNotFoundError(f"Data file not found: {config.data_path}")
        if config.data_path.endswith(".csv"):
            df = pd.read_csv(config.data_path)
        elif config.data_path.endswith(".parquet"):
            df = pd.read_parquet(config.data_path)
        else:
            raise ValueError("Unsupported file type. Please provide a CSV or Parquet file.")
    else:
        df = generate_synthetic_data(300, config.seed)
    return df


def prepare_targets(df: pd.DataFrame, config: Config) -> Tuple[np.ndarray, np.ndarray]:
    placement_map = {label: idx for idx, label in enumerate(CACHE_PLACEMENT_CLASSES)}
    volatility_map = {label: idx for idx, label in enumerate(VOLATILITY_CLASSES)}
    y_place = df[config.target_placement].map(placement_map).values
    y_vol = df[config.target_volatility].map(volatility_map).values
    if np.any(pd.isnull(y_place)) or np.any(pd.isnull(y_vol)):
        raise ValueError("Targets contain unknown class labels.")
    return y_place, y_vol


def main() -> None:
    parser = argparse.ArgumentParser(description="Train cache placement and volatility classifier.")
    parser.add_argument("--data", dest="data_path", default="", help="Path to CSV/Parquet dataset")
    parser.add_argument("--export-onnx", action="store_true", help="Export model to ONNX")
    args = parser.parse_args()

    config = CONFIG
    config.data_path = args.data_path
    config.export_onnx = args.export_onnx

    set_seed(config.seed)
    df = load_data(config)
    validate_columns(df, config)

    train_df, test_df = train_test_split(df, test_size=config.test_size, random_state=config.seed, stratify=df[config.target_placement])
    train_df, val_df = train_test_split(
        train_df,
        test_size=config.val_size,
        random_state=config.seed,
        stratify=train_df[config.target_placement],
    )

    preprocessor = Preprocessor(config)
    preprocessor.fit(train_df)

    X_train_num, X_train_cat = preprocessor.transform(train_df)
    X_val_num, X_val_cat = preprocessor.transform(val_df)
    X_test_num, X_test_cat = preprocessor.transform(test_df)

    y_train_place, y_train_vol = prepare_targets(train_df, config)
    y_val_place, y_val_vol = prepare_targets(val_df, config)
    y_test_place, y_test_vol = prepare_targets(test_df, config)

    train_dataset = TabularDataset(X_train_num, X_train_cat, y_train_place, y_train_vol)
    val_dataset = TabularDataset(X_val_num, X_val_cat, y_val_place, y_val_vol)
    test_dataset = TabularDataset(X_test_num, X_test_cat, y_test_place, y_test_vol)

    train_loader = torch.utils.data.DataLoader(train_dataset, batch_size=config.batch_size, shuffle=True)
    val_loader = torch.utils.data.DataLoader(val_dataset, batch_size=config.batch_size)
    test_loader = torch.utils.data.DataLoader(test_dataset, batch_size=config.batch_size)

    cat_cardinalities = [len(preprocessor.cat_vocabs[col]) for col in config.categorical_features]
    cat_embedding_dims = [config.embedding_dims[col] for col in config.categorical_features]

    model = TabularNN(
        num_numeric=len(config.numeric_features),
        cat_cardinalities=cat_cardinalities,
        cat_embedding_dims=cat_embedding_dims,
        hidden_sizes=config.hidden_sizes,
        dropout=config.dropout,
        num_classes_place=len(CACHE_PLACEMENT_CLASSES),
        num_classes_vol=len(VOLATILITY_CLASSES),
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)

    weights_place = compute_class_weights(y_train_place, CACHE_PLACEMENT_CLASSES)
    weights_vol = compute_class_weights(y_train_vol, VOLATILITY_CLASSES)

    model = train_model(model, train_loader, val_loader, weights_place, weights_vol, config, device)
    test_metrics = evaluate_model(model, test_loader, device)

    baseline_metrics = train_baseline(train_df, val_df, config)

    importance_baseline = baseline_permutation_importance(baseline_metrics, val_df, config)
    importance_nn = nn_feature_ablation(model, preprocessor, val_df.sample(n=min(50, len(val_df))), config, device)

    serialize_artifacts(model, preprocessor, config)
    if config.export_onnx:
        export_to_onnx(model, len(config.numeric_features), len(config.categorical_features), config)

    sample = predict_row(model, preprocessor, test_df.iloc[0].to_dict(), device)

    summary = {
        "nn_test_metrics": test_metrics,
        "baseline_val_metrics": {
            k: baseline_metrics[k]
            for k in ["placement_accuracy", "placement_macro_f1", "volatility_accuracy", "volatility_macro_f1"]
        },
        "baseline_importance": importance_baseline,
        "nn_ablation": importance_nn,
        "sample_prediction": sample,
    }

    os.makedirs(config.model_dir, exist_ok=True)
    with open(os.path.join(config.model_dir, "training_summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
