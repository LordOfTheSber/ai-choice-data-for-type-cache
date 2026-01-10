# Лабораторная работа: модель выбора стратегии кэширования

## Цель
Разработать табличную нейросетевую модель для классификации объектов данных по двум целевым классам:
1. `CachePlacement` (EMBEDDED / SIDECAR / MULTI_LEVEL)
2. `DataVolatilityClass` (STATIC / MEDIUM / DYNAMIC)

На основании предсказаний сформировать рекомендации по параметрам кэширования.

## Входные данные
CSV/Parquet таблица, одна строка = один тип объекта данных. Пример колонок:
- `object_type`
- `avg_size_bytes`, `p95_size_bytes`
- `read_qps`, `write_qps`
- `update_interval_sec`
- `fanout_services`
- `staleness_tolerance_sec`
- `recompute_cost_ms`, `db_latency_ms`
- `is_user_specific`, `is_shared_globally`
- `consistency_required`
- `peakiness`
- Целевые колонки: `y_cache_placement`, `y_volatility`

## Задание
1. Реализовать preprocessing:
   - числовые признаки: медианная импутация + стандартизация
   - категориальные признаки: эмбеддинги (или one‑hot для бейзлайна)
   - обработка пропусков
2. Построить нейросеть MLP с двумя головами классификации.
3. Реализовать обучение (Adam, early stopping, class weights).
4. Посчитать метрики (accuracy, macro‑F1, confusion matrix).
5. Добавить бейзлайн (логистическая регрессия).
6. Реализовать интерпретируемость:
   - permutation importance для бейзлайна
   - feature ablation для нейросети
7. Реализовать функцию инференса, возвращающую JSON с рекомендацией по кэшированию.
8. Опционально экспортировать модель в ONNX.

## Ход работы
1. Запустите обучение на синтетических данных:
   ```bash
   python cache_model.py
   ```
2. Запустите обучение на реальных данных:
   ```bash
   python cache_model.py --data /path/to/data.csv
   ```
3. Посмотрите метрики и пример предсказания, которые выводятся в stdout.
4. Ознакомьтесь с результатами в `artifacts/training_summary.json`.

## Ожидаемый результат
- Обученная модель (`artifacts/tabular_model.pt`).
- JSON‑отчёт `artifacts/training_summary.json` с метриками и интерпретацией.
- Корректное формирование рекомендаций для кэширования.

## Контрольные вопросы
1. Почему для табличных данных подходит MLP?
2. Зачем нужны class weights?
3. В каких случаях следует выбирать multi‑level cache?
4. Чем отличается consistency strong от eventual и как это влияет на TTL?

