import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const params = {
  headers: {
    Accept: 'application/json',
  },
  timeout: '5s',
};

export const options = {
  scenarios: {
    menu_reads: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: 'menuRead',
    },
    popular_menu_reads: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 5,
      maxVUs: 30,
      exec: 'popularMenuRead',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

export function menuRead() {
  const response = http.get(`${baseUrl}/api/v1/menus`, params);
  check(response, {
    'menus response is 200': (value) => value.status === 200,
    'menus response has data': (value) => value.json('data.menus').length > 0,
  });
}

export function popularMenuRead() {
  const response = http.get(`${baseUrl}/api/v1/menus/popular`, params);
  check(response, {
    'popular menus response is 200': (value) => value.status === 200,
    'popular menus response has at most three menus': (value) => value.json('data.menus').length <= 3,
  });
}
