import api, { API_BASE_URL } from './api'

export async function listItems() {
  const response = await api.get('/items')
  return response.data
}

export async function getItemById(id) {
  const response = await api.get(`/items/${id}`)
  return response.data
}

export async function createItem(payload) {
  const response = await api.post('/items', payload)
  return response.data
}

export async function updateItem(id, payload) {
  const response = await api.put(`/items/${id}`, payload)
  return response.data
}

export async function deleteItem(id) {
  await api.delete(`/items/${id}`)
}

export async function uploadImage(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  await api.post(`/items/${id}/image`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** URL para mostrar la imagen almacenada en el backend */
export function getImageUrl(id) {
  return `${API_BASE_URL}/items/${id}/image`
}