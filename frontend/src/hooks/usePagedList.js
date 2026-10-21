import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../api/apiClient'
export default function usePagedList(fetchPage, filters = {}) {
  const [params, setParams] = useSearchParams()
  const page = Math.max(
    0,
    Math.min(1000000, Number.parseInt(params.get('page'), 10) || 0),
  )
  const [result, setResult] = useState({
    content: [],
    totalElements: 0,
    totalPages: 0,
  })
  const [loading, setLoading] = useState(true),
    [error, setError] = useState('')
  const sequence = useRef(0),
    key = JSON.stringify(filters)
  const load = useCallback(async () => {
    const request = ++sequence.current
    setLoading(true)
    setError('')
    try {
      const data = await fetchPage({ page, size: 20, ...JSON.parse(key) })
      if (request === sequence.current) setResult(data)
    } catch (e) {
      if (request === sequence.current) setError(getApiErrorMessage(e))
    } finally {
      if (request === sequence.current) setLoading(false)
    }
  }, [fetchPage, page, key])
  useEffect(() => {
    load()
    return () => {
      sequence.current++
    }
  }, [load])
  useEffect(() => {
    if (
      !loading &&
      !error &&
      result.totalPages > 0 &&
      page >= result.totalPages
    )
      changePage(result.totalPages - 1)
  }, [loading, error, result.totalPages, page])
  function changePage(next) {
    setParams((previous) => {
      const value = new URLSearchParams(previous)
      next ? value.set('page', next) : value.delete('page')
      return value
    })
  }
  return { ...result, page, loading, error, load, changePage }
}
