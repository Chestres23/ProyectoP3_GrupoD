import React, { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { lostItems as mockItems } from '../mocks/mockData'
import { getItemById, getImageUrl } from '../services/itemApi'

const PLACEHOLDER = 'https://images.unsplash.com/photo-1567958451986-2de427a4a0be?w=400&q=60'

export default function ItemDetail(){
  const { id } = useParams()
  const [item, setItem] = useState(mockItems.find((candidate) => candidate.id === Number(id)))

  useEffect(() => {
    const loadItem = async () => {
      try {
        const remoteItem = await getItemById(id)
        setItem(remoteItem)
      } catch {
        setItem(mockItems.find((candidate) => candidate.id === Number(id)) || null)
      }
    }

    loadItem()
  }, [id])

  if(!item) return <div>Item not found</div>

  const resolveImage = () => {
    if (item.hasImage) return getImageUrl(item.id)
    if (item.imageUrl || item.image_url) return item.imageUrl || item.image_url
    return PLACEHOLDER
  }

  return (
    <div className="overflow-hidden rounded-[2rem] bg-white shadow-sm ring-1 ring-slate-200/70">
      <div className="grid lg:grid-cols-[0.9fr_1.1fr]">
        <img src={resolveImage()} onError={(e) => { e.target.src = PLACEHOLDER }} className="h-80 w-full object-cover lg:h-full" />
        <div className="p-6 sm:p-8">
          <div className="inline-flex rounded-full bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-700">{item.status}</div>
          <h2 className="mt-4 text-3xl font-black text-slate-950">{item.name}</h2>
          <div className="mt-2 text-sm text-slate-500">{item.category} • {item.locationFound || item.location_found}</div>
          <p className="mt-6 max-w-2xl text-sm leading-7 text-slate-600">{item.description}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to={`/reclamos/nuevo/${item.id}`} className="rounded-2xl bg-sky-600 px-4 py-3 font-semibold text-white transition hover:bg-sky-500">Crear reclamo</Link>
            <span className="rounded-2xl bg-slate-100 px-4 py-3 font-medium text-slate-700">{item.status}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
