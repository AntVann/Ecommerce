import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.ORDER_URL || 'http://localhost:8087';
const token = __ENV.ACCESS_TOKEN || '';
const payload = __ENV.CHECKOUT_PAYLOAD || '{"cartVersion":1,"shippingAddress":{"name":"Load Test","line1":"1 Test Way","city":"Testville","region":"CA","postalCode":"00000","country":"US"},"billingAddress":{"name":"Load Test","line1":"1 Test Way","city":"Testville","region":"CA","postalCode":"00000","country":"US"},"fakePaymentToken":"mf_fake_approved"}';

export const options = {
  scenarios: {
    finalUnit: { executor: 'shared-iterations', vus: Number(__ENV.VUS || 20), iterations: Number(__ENV.ITERATIONS || 20), maxDuration: __ENV.MAX_DURATION || '2m' },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.20'],
  },
};

export default function () {
  http.setResponseCallback(http.expectedStatuses(201, 202, 400, 401, 403, 409, 422, 503));
  const headers = {
    'Content-Type': 'application/json',
    'Idempotency-Key': `m7-concurrent-${__VU}-${__ITER}`,
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = http.post(`${baseUrl}/api/v1/checkouts`, payload, { headers, tags: { scenario: 'concurrent-checkout' } });
  check(response, { 'checkout is accepted or rejected safely': (r) => [201, 202, 400, 401, 403, 409, 422, 503].includes(r.status) });
}
