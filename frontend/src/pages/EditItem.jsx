import React, { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getItemById, getImageUrl, updateItem, uploadImage } from '../services/itemApi'
import { getSessionUser } from '../services/authStorage'

export default function EditItem() {
  const { id } = useParams()
  const navigate = useNavigate()
  const sessionUser = getSessionUser()

  const [form, setForm] = useState({
    name: '', description: '', category: '', locationFound: '', dateFound: '', imageUrl: '',
  })
  const [currentHasImage, setCurrentHasImage] = useState(false)
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    // Verificar que el usuario puede editar (dueño o admin)
    if (!sessionUser?.token) { navigate('/'); return }

    const fetchItem = async () => {
      try {
        const item = await getItemById(id)
        const sessionUserId = sessionUser.id || sessionUser.sub || sessionUser.userId
        const isOwner = sessionUserId === item.reporterId
        const isAdmin = sessionUser?.isAdmin
        if (!isOwner && !isAdmin) {
          navigate('/objetos')
          return
        }
        setForm({
          name: item.name || '',
          description: item.description || '',
          category: item.category || '',
          locationFound: item.locationFound || '',
          dateFound: item.dateFound || '',
          imageUrl: item.imageUrl || '',
        })
        setCurrentHasImage(item.hasImage)
      } catch {
        setError('No se pudo cargar el objeto.')
      } finally {
        setLoading(false)
      }
    }
    fetchItem()
  }, [id])

  const updateField = (field, value) =>
    setForm((curr) => ({ ...curr, [field]: value }))

  const handleFileChange = (e) => {
    const file = e.target.files[0]
    if (!file) return
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const userId = sessionUser.id || sessionUser.sub || sessionUser.userId
      await updateItem(id, { ...form, userId })
      if (imageFile) {
        await uploadImage(id, imageFile)
      }
      navigate('/objetos')
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'No se pudo actualizar el objeto.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="rounded-[2rem] bg-white p-8 shadow-sm ring-1 ring-slate-200/70 text-slate-500">
        Cargando datos del objeto...
      </div>
    )
  }

  return (
    <div className="max-w-3xl overflow-hidden rounded-[2rem] bg-white shadow-sm ring-1 ring-slate-200/70">
      <div className="border-b border-slate-200 bg-gradient-to-r from-amber-500 to-slate-900 p-6 text-white">
        <p className="text-xs uppercase tracking-[0.25em] text-amber-200">Editar objeto</p>
        <h2 className="mt-2 text-3xl font-black">Editar objeto extraviado</h2>
        <p className="mt-2 text-sm text-amber-100">Modifica los campos y guarda los cambios.</p>
      </div>

      <div className="p-6 sm:p-8">
        {error && (
          <div className="mb-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <form className="grid gap-4" onSubmit={handleSubmit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-semibold text-slate-700">
              Nombre
              <input required value={form.name} onChange={(e) => updateField('name', e.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 p-3 outline-none focus:border-amber-500 focus:bg-white focus:ring-4 focus:ring-amber-100" />
            </label>
            <label className="block text-sm font-semibold text-slate-700">
              Categoría
              <input required value={form.category} onChange={(e) => updateField('category', e.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 p-3 outline-none focus:border-amber-500 focus:bg-white focus:ring-4 focus:ring-amber-100" />
            </label>
          </div>

          <label className="block text-sm font-semibold text-slate-700">
            Descripción
            <textarea required value={form.description} onChange={(e) => updateField('description', e.target.value)}
              className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 p-3 outline-none focus:border-amber-500 focus:bg-white focus:ring-4 focus:ring-amber-100"
              rows="4" />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-semibold text-slate-700">
              Lugar encontrado
              <input required value={form.locationFound} onChange={(e) => updateField('locationFound', e.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 p-3 outline-none focus:border-amber-500 focus:bg-white focus:ring-4 focus:ring-amber-100" />
            </label>
            <label className="block text-sm font-semibold text-slate-700">
              Fecha encontrada
              <input required type="date" value={form.dateFound} onChange={(e) => updateField('dateFound', e.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 p-3 outline-none focus:border-amber-500 focus:bg-white focus:ring-4 focus:ring-amber-100" />
            </label>
          </div>

          {/* Imagen */}
          <div>
            <p className="text-sm font-semibold text-slate-700 mb-2">Foto del objeto</p>
            {/* Imagen actual */}
            {currentHasImage && !imagePreview && (
              <div className="mb-3">
                <p className="text-xs text-slate-400 mb-1">Imagen actual:</p>
                <img
                  src={getImageUrl(id)}
                  alt="Imagen actual"
                  className="h-40 w-full rounded-2xl object-cover border border-slate-200"
                  onError={(e) => { e.target.style.display = 'none' }}
                />
              </div>
            )}
            {/* Nueva imagen */}
            <label
              htmlFor="edit-image"
              className="cursor-pointer inline-flex items-center gap-2 rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50 px-5 py-3 text-sm text-slate-500 transition hover:border-amber-400 hover:text-amber-600"
            >
              📷 {currentHasImage ? 'Cambiar foto' : 'Agregar foto'}
            </label>
            <input id="edit-image" type="file" accept="image/*" className="hidden" onChange={handleFileChange} />
            {imageFile && (
              <span className="ml-3 text-xs text-slate-500">{imageFile.name}</span>
            )}
            {imagePreview && (
              <img src={imagePreview} alt="Vista previa" className="mt-3 h-40 w-full rounded-2xl object-cover border border-slate-200" />
            )}
          </div>

          <div className="flex flex-wrap gap-3">
            <button disabled={saving}
              className="rounded-2xl bg-amber-500 px-6 py-3 font-semibold text-white transition hover:bg-amber-400 disabled:opacity-60">
              {saving ? 'Guardando...' : 'Guardar cambios'}
            </button>
            <button type="button" onClick={() => navigate('/objetos')}
              className="rounded-2xl bg-slate-100 px-6 py-3 font-semibold text-slate-700 transition hover:bg-slate-200">
              Cancelar
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
