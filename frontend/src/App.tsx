import { useEffect, useMemo, useState } from 'react'

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

export function App() {
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

  useEffect(() => { fetch('/api/v1/contours').then(r => r.json()).then(setContours) }, [])

  useEffect(() => {
    fetch('/api/v1/preferences').then(r => r.json()).then((p: UiPreferences) => {
      const pref = { ...defaultPrefs, ...p }
      setContour(pref.contour || '')
      setNamespace(pref.namespace)
      setSelector(pref.selector)
      setFrom(pref.from || '')
      setTo(pref.to || '')
      setPrevious(!!pref.previous)
      setMasterAccess(!!pref.masterAccess)
      setMaxBytes(pref.maxBytes ?? '')
      setSelectedPods(pref.selectedPods || [])
      setSelectedContainers(pref.selectedContainers || [])
    }).catch(() => null)
  }, [])

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

  const loadPods = async () => {
    const params = new URLSearchParams({ contour, namespace })
    if (selector) params.set('selector', selector)
    const podList = await fetch(`/api/v1/pods?${params}`).then(r => r.json())
    setPods(podList)
    const selected = selectedPods.length ? selectedPods.filter(p => podList.includes(p)) : podList
    setSelectedPods(selected)
    await loadContainers(selected)
  }

  const loadContainers = async (podsForReq: string[]) => {
    const params = new URLSearchParams({ contour, namespace })
    if (selector) params.set('selector', selector)
    podsForReq.forEach(p => params.append('pods', p))
    const containerList = await fetch(`/api/v1/containers?${params}`).then(r => r.json())
    setContainers(containerList)
    if (!selectedContainers.length) setSelectedContainers(containerList)
  }

  useEffect(() => {
    if (pods.length) loadContainers(selectedPods)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedPods.join('|')])

  const requestBody = useMemo(() => ({
    contour,
    namespace,
    selector,
    pods: selectedPods,
    containers: selectedContainers,
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

  const doDownload = async () => {
    setLoading(true); setError('')
    try {
      await savePreferences()
      const resp = await fetch('/api/v1/logs/download', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(requestBody) })
      if (!resp.ok) throw new Error(await resp.text())
      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'logs.zip'
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) { setError(String(e)) } finally { setLoading(false) }
  }

  return <main className="page">
    <section className="card">
      <div className="header-row">
        <h1>K8s / OpenShift Log Downloader</h1>
        <button className="ghost" onClick={savePreferences}>Сохранить настройки</button>
      </div>
      {saved && <div className="ok">{saved}</div>}
      <div className="grid">
        <label>Contour<input value={contour} onChange={e => setContour(e.target.value)} list="contours" placeholder="contour-a" /></label>
        <datalist id="contours">{contours.map(c => <option key={c} value={c} />)}</datalist>
        <label>Namespace<input value={namespace} onChange={e => setNamespace(e.target.value)} /></label>
        <label>Selector<input value={selector} onChange={e => setSelector(e.target.value)} placeholder="app=my-service" /></label>
        <label>From (ISO-8601)<input value={from} onChange={e => setFrom(e.target.value)} placeholder="2025-01-01T10:00:00Z" /></label>
        <label>To (ISO-8601)<input value={to} onChange={e => setTo(e.target.value)} placeholder="2025-01-01T11:00:00Z" /></label>
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
          <select multiple value={selectedPods} onChange={e => setSelectedPods(Array.from(e.target.selectedOptions).map(o => o.value))}>
            {pods.map(p => <option key={p} value={p}>{p}</option>)}
          </select>
        </label>
        <label>Containers
          <select multiple value={selectedContainers} onChange={e => setSelectedContainers(Array.from(e.target.selectedOptions).map(o => o.value))}>
            {containers.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
      </div>
      <div className="actions">
        <button onClick={doPreview} disabled={loading}>Preview</button>
        <button onClick={doDownload} disabled={loading}>Download ZIP</button>
      </div>
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
