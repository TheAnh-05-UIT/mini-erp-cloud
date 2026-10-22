import { apiRequest } from '../../shared/api/http-client'

export const UNITS = ['PIECE', 'SET', 'BOX'] as const
export type UnitOfMeasure = typeof UNITS[number]
export type Category = { id: string; code: string; name: string; active: boolean }
export type Product = { id: string; sku: string; name: string; description?: string | null; categoryId: string; categoryCode: string; categoryName: string; unitOfMeasure: UnitOfMeasure; active: boolean }
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export const catalogApi = {
  categories: (page = 0, q = '', active?: boolean) => apiRequest<Page<Category>>(`/api/v1/categories?page=${page}&size=20${q ? `&q=${encodeURIComponent(q)}` : ''}${active === undefined ? '' : `&active=${active}`}`),
  category: (id: string) => apiRequest<Category>(`/api/v1/categories/${id}`),
  createCategory: (body: { code: string; name: string }) => apiRequest<Category>('/api/v1/categories', { method: 'POST', body }),
  updateCategory: (id: string, body: { name: string }) => apiRequest<Category>(`/api/v1/categories/${id}`, { method: 'PATCH', body }),
  setCategoryActive: (id: string, active: boolean) => apiRequest<Category>(`/api/v1/categories/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
  products: (page = 0, q = '', categoryId = '', active?: boolean) => apiRequest<Page<Product>>(`/api/v1/products?page=${page}&size=20${q ? `&q=${encodeURIComponent(q)}` : ''}${categoryId ? `&categoryId=${categoryId}` : ''}${active === undefined ? '' : `&active=${active}`}`),
  product: (id: string) => apiRequest<Product>(`/api/v1/products/${id}`),
  createProduct: (body: { sku: string; name: string; description?: string; categoryId: string; unitOfMeasure: UnitOfMeasure }) => apiRequest<Product>('/api/v1/products', { method: 'POST', body }),
  updateProduct: (id: string, body: { name: string; description?: string; categoryId: string; unitOfMeasure: UnitOfMeasure }) => apiRequest<Product>(`/api/v1/products/${id}`, { method: 'PATCH', body }),
  setProductActive: (id: string, active: boolean) => apiRequest<Product>(`/api/v1/products/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
}
