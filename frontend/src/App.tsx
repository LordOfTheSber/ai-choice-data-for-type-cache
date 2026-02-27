import { useEffect, useMemo, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import type { RootState } from './store'
import { setContainersCache, setPodsCache } from './store'

type PreviewLine = { pod: string, container: string, line: string }
type PreviewResponse = { lines: PreviewLine[], stats: { totalLines: number, unparsedTimestampCount: number, errors: number } }
type UiPreferences = {
  contour: string | null
  namespace: string
  selector: string
  from: string | null
  to: string | null
  previous: boolean
  maxBytes: number | null
  masterAccess: boolean
  selectedPods: string[] | null
  selectedContainers: string[] | null
}
type CollectStatus = { jobId: string, status: 'RUNNING' | 'DONE' | 'FAILED' | string, message: string, sizeBytes: number }

const defaultPrefs: UiPreferences = {
  contour: null,
  namespace: 'default',
  selector: '',
  from: null,
  to: null,
  previous: false,
  maxBytes: null,
  masterAccess: false,
  selectedPods: [],
  selectedContainers: []
}

const asStringArray = (value: unknown): string[] => {
  if (Array.isArray(value)) return value.filter((v): v is string => typeof v === 'string')
  if (typeof value === 'string') return value.split(',').map(v => v.trim()).filter(Boolean)
  return []
}

export function App() {
  const dispatch = useDispatch()
  const podsCache = useSelector((s: RootState) => s.cache.pods)
  const containersCache = useSelector((s: RootState) => s.cache.containers)

  const [contours, setContours] = useState<string[]>([])
  const [contour, setContour] = useState('')
  const [namespace, setNamespace] = useState('default')
  const [selector, setSelector] = useState('')
  const [pods, setPods] = useState<string[]>([])
  const [selectedPods, setSelectedPods] = useState<string[]>([])
  const [containers, setContainers] = useState<string[]>([])
  const [selectedContainers, setSelectedContainers] = useState<string[]>([])
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [pollIntervalSeconds, setPollIntervalSeconds] = useState(30)
  const [previous, setPrevious] = useState(false)
  const [masterAccess, setMasterAccess] = useState(false)
  const [maxBytes, setMaxBytes] = useState<number | ''>('')
  const [preview, setPreview] = useState<PreviewResponse | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [saved, setSaved] = useState('')

  const [collectJobId, setCollectJobId] = useState<string | null>(null)
  const [collectStatus, setCollectStatus] = useState<CollectStatus | null>(null)

  useEffect(() => {
    fetch('/api/v1/contours').then(r => r.json()).then((v) => setContours(asStringArray(v))).catch(() => setContours([]))
  }, [])

  useEffect(() => {
    fetch('/api/v1/preferences').then(r => r.json()).then((p: Partial<UiPreferences>) => {
      const pref = { ...defaultPrefs, ...p }
      setContour(pref.contour || '')
      setNamespace(pref.namespace || 'default')
      setSelector(pref.selector || '')
      setFrom(pref.from || '')
      setTo(pref.to || '')
      setPrevious(!!pref.previous)
      setMasterAccess(!!pref.masterAccess)
      setMaxBytes(pref.maxBytes ?? '')
      setSelectedPods(asStringArray(pref.selectedPods))
      setSelectedContainers(asStringArray(pref.selectedContainers))
    }).catch(() => null)
  }, [])

  useEffect(() => {
    if (!collectJobId) return
    const every = Math.max(1000, pollIntervalSeconds * 1000)
    const timer = setInterval(async () => {
      const resp = await fetch(`/api/v1/logs/collect/${collectJobId}`)
      if (!resp.ok) return
      const status: CollectStatus = await resp.json()
      setCollectStatus(status)
      if (status.status === 'DONE' || status.status === 'FAILED') {
        clearInterval(timer)
      }
    }, every)
    return () => clearInterval(timer)
  }, [collectJobId, pollIntervalSeconds])

  const savePreferences = async () => {
    const payload: UiPreferences = {
      contour: contour || null,
      namespace,
      selector,
      from: from || null,
      to: to || null,
      previous,
      maxBytes: maxBytes === '' ? null : maxBytes,
      masterAccess,
      selectedPods,
      selectedContainers
    }
    await fetch('/api/v1/preferences', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    setSaved('Сохранено')
    setTimeout(() => setSaved(''), 1200)
  }

  const podsCacheKey = `${contour}|${namespace}|${selector}`

  const loadPods = async () => {
    let podList = podsCache[podsCacheKey]
    if (!podList) {
      const params = new URLSearchParams({ contour, namespace })
      if (selector) params.set('selector', selector)
      const podResponse = await fetch(`/api/v1/pods?${params}`).then(r => r.json())
      podList = asStringArray(podResponse)
      dispatch(setPodsCache({ key: podsCacheKey, items: podList }))
    }

    setPods(podList)
    const currentSelected = asStringArray(selectedPods)
    const selected = currentSelected.length ? currentSelected.filter(p => podList.includes(p)) : podList
    setSelectedPods(selected)
    await loadContainers(selected)
  }

  const loadContainers = async (podsForReq: unknown) => {
    const normalizedPods = asStringArray(podsForReq)
    const containerCacheKey = `${contour}|${namespace}|${selector}|${normalizedPods.slice().sort().join(',')}`

    let containerList = containersCache[containerCacheKey]
    if (!containerList) {
      const params = new URLSearchParams({ contour, namespace })
      if (selector) params.set('selector', selector)
      normalizedPods.forEach(p => params.append('pods', p))
      const containerResponse = await fetch(`/api/v1/containers?${params}`).then(r => r.json())
      containerList = asStringArray(containerResponse)
      dispatch(setContainersCache({ key: containerCacheKey, items: containerList }))
    }

    setContainers(containerList)
    const prevSelected = asStringArray(selectedContainers)
    if (!prevSelected.length) setSelectedContainers(containerList)
    else setSelectedContainers(prevSelected.filter(c => containerList.includes(c)))
  }

  useEffect(() => {
    if (pods.length) loadContainers(asStringArray(selectedPods))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [asStringArray(selectedPods).join('|')])

  const requestBody = useMemo(() => ({
    contour,
    namespace,
    selector,
    pods: asStringArray(selectedPods),
    containers: asStringArray(selectedContainers),
    from: from || null,
    to: to || null,
    previous,
    maxBytes: maxBytes === '' ? null : maxBytes,
    bestEffort: true,
    pollIntervalSeconds,
    masterAccess
  }), [contour, namespace, selector, selectedPods, selectedContainers, from, to, previous, maxBytes, pollIntervalSeconds, masterAccess])

  const doPreview = async () => {
    setLoading(true); setError('')
    try {
      await savePreferences()
      const resp = await fetch('/api/v1/logs/preview', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...requestBody, lines: 300 }) })
      if (!resp.ok) throw new Error(await resp.text())
      setPreview(await resp.json())
    } catch (e) { setError(String(e)) } finally { setLoading(false) }
  }

  const startCollect = async () => {
    setLoading(true); setError('')
    try {
      await savePreferences()
      const resp = await fetch('/api/v1/logs/collect', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(requestBody) })
      if (!resp.ok) throw new Error(await resp.text())
      const data = await resp.json()
      setCollectJobId(data.jobId)
      setCollectStatus({ jobId: data.jobId, status: data.status, message: 'Started', sizeBytes: 0 })
    } catch (e) { setError(String(e)) } finally { setLoading(false) }
  }

  const downloadCollected = async () => {
    if (!collectJobId) return
    const resp = await fetch(`/api/v1/logs/collect/${collectJobId}/download`)
    if (!resp.ok) throw new Error(await resp.text())
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `logs-${collectJobId}.zip`
    a.click()
    URL.revokeObjectURL(url)
  }

  return <main className="page">
    <section className="card">
      <div className="header-row">
        <h1>K8s / OpenShift Log Downloader</h1>
        <button className="ghost" onClick={savePreferences}>Сохранить настройки</button>
      </div>
      {saved && <div className="ok">{saved}</div>}
      <div className="hint">Период выбирается по московскому времени (как в логах: yyyy-MM-dd HH:mm:ss,SSS).</div>
      <div className="grid">
        <label>Contour<input value={contour} onChange={e => setContour(e.target.value)} list="contours" placeholder="sb_mg" /></label>
        <datalist id="contours">{contours.map(c => <option key={c} value={c} />)}</datalist>
        <label>Namespace<input value={namespace} onChange={e => setNamespace(e.target.value)} /></label>
        <label>Selector<input value={selector} onChange={e => setSelector(e.target.value)} placeholder="app=my-service" /></label>
        <label>From (MSK)<input type="datetime-local" value={from} onChange={e => setFrom(e.target.value)} /></label>
        <label>To (MSK)<input type="datetime-local" value={to} onChange={e => setTo(e.target.value)} /></label>
        <label>Poll interval, sec<input type="number" value={pollIntervalSeconds} onChange={e => setPollIntervalSeconds(Number(e.target.value || 30))} /></label>
        <label>Max bytes<input type="number" value={maxBytes} onChange={e => setMaxBytes(e.target.value ? Number(e.target.value) : '')} /></label>
      </div>
      <div className="row">
        <label><input type="checkbox" checked={previous} onChange={e => setPrevious(e.target.checked)} /> previous logs</label>
        <label><input type="checkbox" checked={masterAccess} onChange={e => setMasterAccess(e.target.checked)} /> master token</label>
        <button onClick={loadPods}>Обновить pods/containers</button>
      </div>
      <div className="selectors">
        <label>Pods
          <select multiple value={asStringArray(selectedPods)} onChange={e => setSelectedPods(Array.from(e.target.selectedOptions).map(o => o.value))}>
            {pods.map(p => <option key={p} value={p}>{p}</option>)}
          </select>
        </label>
        <label>Containers
          <select multiple value={asStringArray(selectedContainers)} onChange={e => setSelectedContainers(Array.from(e.target.selectedOptions).map(o => o.value))}>
            {containers.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
      </div>
      <div className="actions">
        <button onClick={doPreview} disabled={loading}>Preview</button>
        <button onClick={startCollect} disabled={loading}>Сбор логов</button>
        <button onClick={downloadCollected} disabled={!collectStatus || collectStatus.status !== 'DONE'}>Скачать собранные логи</button>
      </div>
      {collectStatus && <p>Job {collectStatus.jobId}: {collectStatus.status} ({collectStatus.sizeBytes} bytes) {collectStatus.message}</p>}
      {loading && <p>Выполняется...</p>}
      {error && <pre className="error">{error}</pre>}
    </section>

    {preview && <section className="card">
      <h2>Preview</h2>
      <p>Lines: {preview.stats.totalLines} · Unparsed: {preview.stats.unparsedTimestampCount} · Errors: {preview.stats.errors}</p>
      <pre>{preview.lines.map(l => `[${l.pod}/${l.container}] ${l.line}`).join('\n')}</pre>
    </section>}
  </main>
}
