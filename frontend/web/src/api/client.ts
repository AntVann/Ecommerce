import type { ProblemDetails, Product, SearchResponse, Cart, Order, OrderPage, Shipment, Seller, InventoryItem, PublicAvailability, AuditEvent, Image } from './types';

export type Service = 'identity' | 'seller' | 'catalog' | 'inventory' | 'search' | 'cart' | 'order' | 'notification';
const bases: Record<Service, string> = {
  identity: '/identity/api/v1', seller: '/seller/api/v1', catalog: '/catalog/api/v1', inventory: '/inventory/api/v1',
  search: '/search/api/v1', cart: '/cart/api/v1', order: '/order/api/v1', notification: '/notification/api/v1'
};

let accessToken: string | null = null;
export const session = { get token() { return accessToken; }, set token(value: string | null) { accessToken = value; } };

function csrfCookie(): string | undefined {
  return document.cookie.split('; ').find((part) => part.startsWith('MF_CSRF=') || part.startsWith('MARKETFLOW_GUEST_CSRF='))?.split('=')[1];
}

export class ApiError extends Error {
  constructor(public readonly problem: ProblemDetails, public readonly response: Response) { super(problem.detail || problem.title || 'Request failed'); }
}

export async function request<T>(service: Service, path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  headers.set('X-Correlation-ID', crypto.randomUUID());
  if (path.includes('/auth/refresh') || path.includes('/auth/logout') || service === 'cart') {
    const csrf = csrfCookie(); if (csrf) headers.set('X-CSRF-Token', decodeURIComponent(csrf));
  }
  const response = await fetch(`${bases[service]}${path}`, { ...init, headers, credentials: 'include' });
  if (response.status === 401 && retry && !path.includes('/auth/')) {
    try { await request<unknown>('identity', '/auth/refresh', { method: 'POST' }, false); return request<T>(service, path, init, false); } catch { accessToken = null; }
  }
  if (!response.ok) {
    let problem: ProblemDetails = { status: response.status, title: response.statusText };
    try { problem = { ...problem, ...(await response.json()) }; } catch { /* non-json failure */ }
    throw new ApiError(problem, response);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  register: (email: string, password: string) => request<void>('identity', '/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  login: async (email: string, password: string) => { const result = await request<{ accessToken: string; expiresIn: number }>('identity', '/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }); accessToken = result.accessToken; return result; },
  refresh: async () => { const result = await request<{ accessToken: string; expiresIn: number }>('identity', '/auth/refresh', { method: 'POST' }); accessToken = result.accessToken; return result; },
  logout: async () => { await request<void>('identity', '/auth/logout', { method: 'POST' }); accessToken = null; },
  categories: () => request<unknown[]>('catalog', '/categories'),
  product: (id: string) => request<Product>('catalog', `/products/${id}`),
  createProduct: (sellerId: string, payload: unknown) => request<Product>('catalog', `/sellers/${sellerId}/products`, { method: 'POST', body: JSON.stringify(payload) }),
  sellerProducts: (sellerId: string, status?: string) => request<Product[]>('catalog', `/sellers/${sellerId}/products?limit=100${status ? `&status=${encodeURIComponent(status)}` : ''}`),
  uploadImage: (sellerId: string, productId: string, file: File, altText: string, displayOrder = 0) => { const body = new FormData(); body.append('file', file); body.append('altText', altText); body.append('displayOrder', String(displayOrder)); return request<Image>('catalog', `/sellers/${sellerId}/products/${productId}/images/upload`, { method: 'POST', body }); },
  search: (params: URLSearchParams) => request<SearchResponse>('search', `/products?${params.toString()}`),
  cart: () => request<Cart>('cart', '/cart'),
  addToCart: (variantId: string, quantity: number) => request<Cart>('cart', '/cart/items', { method: 'POST', body: JSON.stringify({ variantId, quantity }) }),
  updateCart: (variantId: string, quantity: number, version: number) => request<Cart>('cart', `/cart/items/${variantId}`, { method: 'PATCH', headers: { 'If-Match': `"${version}"` }, body: JSON.stringify({ quantity }) }),
  removeFromCart: (variantId: string, version: number) => request<Cart>('cart', `/cart/items/${variantId}`, { method: 'DELETE', headers: { 'If-Match': `"${version}"` } }),
  clearCart: (version: number) => request<void>('cart', '/cart', { method: 'DELETE', headers: { 'If-Match': `"${version}"` } }),
  mergeCart: () => request<Cart>('cart', '/cart/merge', { method: 'POST' }),
  checkout: (payload: unknown, idempotencyKey: string) => request<Order>('order', '/checkouts', { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(payload) }),
  pay: (orderId: string, fakePaymentToken: string, idempotencyKey: string) => request<Order>('order', `/orders/${orderId}/payment-authorizations`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify({ fakePaymentToken }) }),
  orders: (cursor?: string) => request<OrderPage>('order', `/orders?limit=25${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`),
  order: (id: string) => request<Order>('order', `/orders/${id}`),
  shipments: (id: string) => request<Shipment[]>('order', `/orders/${id}/shipments`),
  applySeller: (payload: unknown) => request<Seller>('seller', '/seller-applications', { method: 'POST', body: JSON.stringify(payload) }),
  seller: (id: string) => request<Seller>('seller', `/sellers/${id}`),
  applications: (status?: string) => request<Seller[]>('seller', `/admin/seller-applications?limit=100${status ? `&status=${status}` : ''}`),
  sellerAction: (id: string, action: 'approve' | 'reject' | 'suspend', version: number, reason?: string) => request<Seller>('seller', `/admin/sellers/${id}/${action}`, { method: 'POST', headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': crypto.randomUUID() }, body: action === 'approve' ? undefined : JSON.stringify({ reason }) }),
  sellerOrders: (sellerId: string) => request<{ items: Order[]; nextCursor?: string }>('order', `/sellers/${sellerId}/orders?limit=25`),
  inventory: (sellerId: string) => request<InventoryItem[]>('inventory', `/sellers/${sellerId}/inventory`),
  availability: (variantId: string) => request<PublicAvailability>('inventory', `/variants/${variantId}/availability`),
  adjustInventory: (sellerId: string, variantId: string, delta: number, version: number) => request<InventoryItem>('inventory', `/sellers/${sellerId}/inventory/${variantId}/adjustments`, { method: 'POST', headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ quantityDelta: delta, reasonCode: 'UI_ADJUSTMENT' }) }),
  createShipment: (sellerId: string, orderId: string, payload: unknown) => request<Shipment>('order', `/sellers/${sellerId}/orders/${orderId}/shipments`, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(payload) }),
  auditEvents: () => request<AuditEvent[]>('identity', '/admin/audit-events?limit=100'),
  sellerAuditEvents: () => request<AuditEvent[]>('seller', '/admin/audit-events?limit=100')
};
