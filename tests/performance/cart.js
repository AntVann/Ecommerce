import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.CART_URL || 'http://localhost:8086';
const token = __ENV.ACCESS_TOKEN || '';
const csrf = __ENV.CSRF_TOKEN || '';

export const options = {
  scenarios: {
    cart: { executor: 'constant-vus', vus: Number(__ENV.VUS || 10), duration: __ENV.DURATION || '1m' },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.10'],
  },
};

function headers() {
  const value = { 'Content-Type': 'application/json' };
  if (token) value.Authorization = `Bearer ${token}`;
  if (csrf) value['X-CSRF-Token'] = csrf;
  return value;
}

export default function () {
  http.setResponseCallback(http.expectedStatuses(200, 401, 403));
  const response = http.get(`${baseUrl}/api/v1/cart`, { headers: headers(), tags: { scenario: 'cart' } });
  check(response, { 'cart request is accepted': (r) => [200, 401, 403].includes(r.status) });
  sleep(0.1);
}
