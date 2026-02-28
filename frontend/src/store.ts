import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit'

export type PodCacheEntry = { key: string, items: string[] }
export type ContainerCacheEntry = { key: string, items: string[] }

type CacheState = {
  pods: Record<string, string[]>
  containers: Record<string, string[]>
}

const initialState: CacheState = { pods: {}, containers: {} }

const cacheSlice = createSlice({
  name: 'cache',
  initialState,
  reducers: {
    setPodsCache(state, action: PayloadAction<PodCacheEntry>) {
      state.pods[action.payload.key] = action.payload.items
    },
    setContainersCache(state, action: PayloadAction<ContainerCacheEntry>) {
      state.containers[action.payload.key] = action.payload.items
    }
  }
})

export const { setPodsCache, setContainersCache } = cacheSlice.actions

export const store = configureStore({
  reducer: { cache: cacheSlice.reducer }
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
