import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.SEARCH_URL || 'http://localhost:8085';

export const options = {
  scenarios: {
    search: { executor: 'constant-vus', vus: Number(__ENV.VUS || 10), duration: __ENV.DURATION || '1m' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const term = __ENV.QUERY || 'portfolio';
  const response = http.get(`${baseUrl}/api/v1/products?q=${encodeURIComponent(term)}&limit=25`, {
    tags: { scenario: 'product-search' },
  });
  check(response, { 'search is available': (r) => r.status === 200 });
  sleep(0.1);
}
