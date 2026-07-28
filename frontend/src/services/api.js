import axios from 'axios'

export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

const api = axios.create({
  baseURL: API_BASE_URL
})

api.interceptors.request.use((config) => {
  const rawSession = localStorage.getItem('campuslost_user')
  if (rawSession) {
    try {
      const session = JSON.parse(rawSession)
      if (session?.token) {
        config.headers.Authorization = `Bearer ${session.token}`
      }
    } catch {
      localStorage.removeItem('campuslost_user')
    }
  }

  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config
    const maxRetries = 2

    if (!config) {
      return Promise.reject(error)
    }

    const status = error.response?.status
    const shouldRetry =
      error.message === 'Network Error' ||
      error.code === 'ECONNABORTED' ||
      error.code === 'ERR_NETWORK' ||
      [502, 503, 504].includes(status)

    if (!shouldRetry) {
      return Promise.reject(error)
    }

    config.__retryCount = config.__retryCount || 0
    if (config.__retryCount >= maxRetries) {
      return Promise.reject(error)
    }

    config.__retryCount += 1
    await new Promise((resolve) => setTimeout(resolve, 200 * config.__retryCount))
    return api(config)
  }
)

export default api
