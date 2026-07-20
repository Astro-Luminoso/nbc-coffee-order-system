import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const userId = Number(__ENV.USER_ID || '5');
const menuId = Number(__ENV.MENU_ID || '1');
const chargeAmount = Number(__ENV.CHARGE_AMOUNT || '6000');

export const options = {
  scenarios: {
    charge_then_pay: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 100,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const chargeKey = idempotencyKey('charge');
  const chargeResponse = http.post(
    `${baseUrl}/api/v1/users/${userId}/point-charges`,
    JSON.stringify({ amount: chargeAmount }),
    requestParams(chargeKey)
  );

  const charged = check(chargeResponse, {
    'charge response is 200': (value) => value.status === 200,
    'charge returns a positive balance': (value) => value.json('data.balance') > 0,
  });
  if (!charged) {
    return;
  }

  const paymentResponse = http.post(
    `${baseUrl}/api/v1/orders`,
    JSON.stringify({ userId, menuId }),
    requestParams(idempotencyKey('payment'))
  );
  check(paymentResponse, {
    'payment response is 201': (value) => value.status === 201,
    'payment response has an order ID': (value) => value.json('data.orderId') > 0,
  });

  sleep(0.1);
}

function requestParams(key) {
  return {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': key,
    },
    timeout: '5s',
  };
}

function idempotencyKey(operation) {
  return `${operation}-${__VU}-${__ITER}-${Date.now()}`;
}
