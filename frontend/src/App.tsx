import { useEffect, useState } from 'react'

type PreviewLine = { pod: string, container: string, line: string }

type PreviewResponse = { lines: PreviewLine[], stats: { totalLines: number, unparsedTimestampCount: number, errors: number } }

export function App() {
  const [contours, setContours] = useState<string[]>([])
  const [contour, setContour] = useState('')
  const [namespace, setNamespace] = useState('default')
  const [selector, setSelector] = useState('')
  const [pods, setPods] = useState<string[]>([])
  const [selectedPods, setSelectedPods] = useState<string[]>([])
  const [containers, setContainers] = useState<string[]>([])
  const [container, setContainer] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [previous, setPrevious] = useState(false)
  const [maxBytes, setMaxBytes] = useState<number | ''>('')
  const [preview, setPreview] = useState<PreviewResponse | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => { fetch('/api/v1/contours').then(r => r.json()).then(setContours) }, [])

  const loadPods = async () => {
    const params = new URLSearchParams({ contour, namespace })
    if (selector) params.set('selector', selector)
    const list = await fetch(`/api/v1/pods?${params}`).then(r => r.json())
    setPods(list)
    setSelectedPods(list)
    if (list[0]) {
      const c = await fetch(`/api/v1/pods/${list[0]}/containers?contour=${encodeURIComponent(contour)}&namespace=${encodeURIComponent(namespace)}`).then(r => r.json())
      setContainers(c)
      setContainer(c[0] || '')
    }
  }

  const body = () => ({
    contour,
    namespace,
    selector,
    pods: selectedPods,
    containers: container ? [container] : [],
    from: from || null,
    to: to || null,
    previous,
    maxBytes: maxBytes === '' ? null : maxBytes,
    bestEffort: true,
    masterAccess: false
  })

  const doPreview = async () => {
    setLoading(true); setError('')
    try {
      const resp = await fetch('/api/v1/logs/preview', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...body(), lines: 200 }) })
      if (!resp.ok) throw new Error(await resp.text())
      setPreview(await resp.json())
    } catch (e) { setError(String(e)) } finally { setLoading(false) }
  }

  const doDownload = async () => {
    setLoading(true); setError('')
    try {
      const resp = await fetch('/api/v1/logs/download', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body()) })
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

  return <div className="container">
    <h1>K8s/OpenShift Log Downloader</h1>
    <div className="grid">
      <input placeholder="contour" value={contour} onChange={e => setContour(e.target.value)} list="contours" />
      <datalist id="contours">{contours.map(c => <option key={c} value={c} />)}</datalist>
      <input placeholder="namespace" value={namespace} onChange={e => setNamespace(e.target.value)} />
      <input placeholder="label selector app=my-app" value={selector} onChange={e => setSelector(e.target.value)} />
      <button onClick={loadPods}>Load pods</button>
      <select multiple value={selectedPods} onChange={e => setSelectedPods(Array.from(e.target.selectedOptions).map(o => o.value))}>
        {pods.map(p => <option key={p} value={p}>{p}</option>)}
      </select>
      <select value={container} onChange={e => setContainer(e.target.value)}>
        <option value="">All containers</option>
        {containers.map(c => <option key={c} value={c}>{c}</option>)}
      </select>
      <input type="datetime-local" value={from} onChange={e => setFrom(e.target.value ? new Date(e.target.value).toISOString() : '')} />
      <input type="datetime-local" value={to} onChange={e => setTo(e.target.value ? new Date(e.target.value).toISOString() : '')} />
      <label><input type="checkbox" checked={previous} onChange={e => setPrevious(e.target.checked)} /> previous</label>
      <input type="number" placeholder="maxBytes" value={maxBytes} onChange={e => setMaxBytes(e.target.value ? Number(e.target.value) : '')} />
      <button onClick={doPreview} disabled={loading}>Preview</button>
      <button onClick={doDownload} disabled={loading}>Download ZIP</button>
    </div>
    {error && <pre className="error">{error}</pre>}
    {loading && <p>In progress...</p>}
    {preview && <>
      <p>Total lines: {preview.stats.totalLines} | Unparsed timestamps: {preview.stats.unparsedTimestampCount} | Errors: {preview.stats.errors}</p>
      <pre>{preview.lines.map(l => `[${l.pod}/${l.container}] ${l.line}`).join('\n')}</pre>
    </>}
  </div>
}
