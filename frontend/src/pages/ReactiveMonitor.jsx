import React, { useState, useEffect } from 'react'

const API_BASE = import.meta.env.VITE_API_URL || '/api'

export default function ReactiveMonitor() {
  const [stats, setStats] = useState(null)
  const [events, setEvents] = useState([])
  const [simulating, setSimulating] = useState(false)

  const fetchStats = async () => {
    try {
      const rawSession = localStorage.getItem('campuslost_user')
      const session = rawSession ? JSON.parse(rawSession) : null
      const token = session?.token

      const res = await fetch(`${API_BASE}/reactive/claims/stats`, {
        headers: {
          'Authorization': token ? `Bearer ${token}` : undefined
        }
      })
      if (res.ok) {
        const data = await res.json()
        setStats(data)
      }
    } catch (err) {
      console.error('Error al obtener estadísticas', err)
    }
  }

  useEffect(() => {
    fetchStats()
    const interval = setInterval(fetchStats, 5000)
    return () => clearInterval(interval)
  }, [])

  const pushEvent = (data) => {
    setEvents(prev => [data, ...prev].slice(0, 50))
    fetchStats()
  }

  const handleEvent = (e) => {
    if (!e?.data) return
    try {
      const data = JSON.parse(e.data)
      pushEvent(data)
    } catch (err) {
      console.warn('SSE event no procesado', err)
    }
  }

  useEffect(() => {
    const eventSource = new EventSource(`${API_BASE}/reactive/claims/stream`)

    eventSource.addEventListener('open', () => {
      console.info('SSE conectado a /reactive/claims/stream')
    })
    eventSource.addEventListener('message', handleEvent)
    eventSource.addEventListener('claim-event', handleEvent)
    eventSource.addEventListener('simulated-event', handleEvent)

    eventSource.onerror = (e) => {
      console.error('SSE error', e)
    }

    return () => eventSource.close()
  }, [])

  const toggleSimulation = async () => {
    setSimulating(true)
    try {
      const simSource = new EventSource(`${API_BASE}/reactive/claims/simulate`)

      simSource.addEventListener('simulated-event', handleEvent)
      simSource.onerror = (e) => {
        console.error('SSE simulation error', e)
      }

      setTimeout(() => {
        simSource.close()
        setSimulating(false)
      }, 15000)
    } catch (err) {
      console.error(err)
      setSimulating(false)
    }
  }

  const getTypeBadge = (type) => {
    const colors = {
      ITEM_CREATED: 'bg-emerald-600',
      ITEM_UPDATED: 'bg-amber-500',
      ITEM_DELETED: 'bg-red-600',
      ITEM_CLAIMED: 'bg-blue-600',
      ITEM_DELIVERED: 'bg-green-600',
      IMAGE_UPLOADED: 'bg-purple-500',
      CLAIM_CREATED: 'bg-cyan-600',
      CLAIM_APPROVED: 'bg-green-500',
      CLAIM_REJECTED: 'bg-red-500',
      CLAIM_DELETED: 'bg-gray-500',
      USER_REGISTERED: 'bg-indigo-500',
      USER_DEACTIVATED: 'bg-orange-500'
    }
    const label = {
      ITEM_CREATED: 'OBJETO CREADO',
      ITEM_UPDATED: 'OBJETO ACTUALIZADO',
      ITEM_DELETED: 'OBJETO ELIMINADO',
      ITEM_CLAIMED: 'OBJETO RECLAMADO',
      ITEM_DELIVERED: 'OBJETO ENTREGADO',
      IMAGE_UPLOADED: 'IMAGEN SUBIDA',
      CLAIM_CREATED: 'RECLAMO CREADO',
      CLAIM_APPROVED: 'RECLAMO APROBADO',
      CLAIM_REJECTED: 'RECLAMO RECHAZADO',
      CLAIM_DELETED: 'RECLAMO ELIMINADO',
      USER_REGISTERED: 'USUARIO REGISTRADO',
      USER_DEACTIVATED: 'USUARIO DESACTIVADO'
    }
    return {
      color: colors[type] || 'bg-slate-500',
      label: label[type] || type
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 animate-in fade-in zoom-in-95 duration-500">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-4xl font-black tracking-tight text-slate-900">Monitor Reactivo (WebFlux)</h1>
          <p className="mt-2 text-lg text-slate-600">
            Todas las acciones del sistema en tiempo real vía SSE.
          </p>
        </div>
        <button
          onClick={toggleSimulation}
          disabled={simulating}
          className="rounded-2xl bg-indigo-600 px-6 py-3 font-semibold text-white shadow-lg shadow-indigo-600/30 transition hover:-translate-y-1 hover:bg-indigo-700 disabled:opacity-50 disabled:hover:translate-y-0"
        >
          {simulating ? 'Simulando...' : 'Simular Actividad'}
        </button>
      </header>

      <section className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard title="Total Objetos" value={stats?.totalItems || 0} />
        <StatCard title="Reclamos Activos" value={stats?.totalClaims || 0} />
        <StatCard title="Reclamos Pendientes" value={stats?.pendingClaims || 0} />
        <StatCard title="Aprobados" value={stats?.approvedClaims || 0} color="text-emerald-600" />
        <StatCard title="Rechazados" value={stats?.rejectedClaims || 0} color="text-red-600" />
        <StatCard 
          title="Tasa de Aprobación" 
          value={`${stats?.approvalRate ? stats.approvalRate.toFixed(1) : 0}%`} 
          color="text-indigo-600" 
        />
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-xl shadow-slate-200/40">
        <h2 className="mb-4 text-2xl font-bold">Log de actividades en tiempo real</h2>
        <div className="space-y-2">
          {events.length === 0 ? (
            <p className="text-slate-500">Esperando eventos en vivo...</p>
          ) : (
            events.map((ev, idx) => {
              const badge = getTypeBadge(ev.type)
              return (
                <div key={idx} className="flex items-center gap-3 rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 transition-all duration-300">
                  <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-[10px] font-bold text-white ${badge.color}`}>
                    {badge.label}
                  </span>
                  <p className="flex-1 text-sm text-slate-800">
                    {ev.description || `${ev.itemName} - ${ev.userName}`}
                  </p>
                  <span className="shrink-0 text-[11px] text-slate-400">
                    {ev.timestamp ? new Date(ev.timestamp).toLocaleTimeString() : ''}
                  </span>
                </div>
              )
            })
          )}
        </div>
      </section>
    </div>
  )
}

function StatCard({ title, value, color = "text-slate-900" }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-3xl border border-slate-200 bg-white p-6 shadow-xl shadow-slate-200/40 transition hover:-translate-y-1">
      <div className="text-sm font-semibold uppercase tracking-wider text-slate-500">{title}</div>
      <div className={`mt-2 text-4xl font-black tracking-tighter ${color}`}>{value}</div>
    </div>
  )
}
