import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.CATALOG_URL || 'http://localhost:8083';
const productId = __ENV.PRODUCT_ID || '00000000-0000-0000-0000-000000000000';

export const options = {
  scenarios: {
    browse: { executor: 'constant-vus', vus: Number(__ENV.VUS || 10), duration: __ENV.DURATION || '1m' },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/v1/products/${productId}`, {
    tags: { scenario: 'product-browse' },
  });
  check(response, { 'product detail is available': (r) => r.status === 200 });
  sleep(0.1);
}
