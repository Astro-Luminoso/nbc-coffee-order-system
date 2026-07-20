import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const userId = Number(__ENV.USER_ID || '5');
const menuId = Number(__ENV.MENU_ID || '1');
const chargeAmount = Number(__ENV.CHARGE_AMOUNT || '10000');

export const options = {
  scenarios: {
    concurrent_same_key_payment: {
      executor: 'per-vu-iterations',
      vus: 20,
      iterations: 1,
      maxDuration: '30s',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const menusResponse = http.get(`${baseUrl}/api/v1/menus`, readParams());
  if (menusResponse.status !== 200) {
    throw new Error('메뉴 목록을 조회할 수 없습니다.');
  }
  const menu = menusResponse.json('data.menus').find((value) => value.id === menuId);
  if (!menu) {
    throw new Error(`메뉴를 찾을 수 없습니다. menuId=${menuId}`);
  }

  const chargeResponse = http.post(
    `${baseUrl}/api/v1/users/${userId}/point-charges`,
    JSON.stringify({ amount: chargeAmount }),
    requestParams(`idempotency-setup-${Date.now()}`)
  );
  if (chargeResponse.status !== 200) {
    throw new Error(`테스트 사용자 충전에 실패했습니다. status=${chargeResponse.status}`);
  }

  return {
    orderKey: `idempotency-payment-${Date.now()}`,
    expectedRemainingBalance: chargeResponse.json('data.balance') - menu.price,
  };
}

export default function (data) {
  const response = http.post(
    `${baseUrl}/api/v1/orders`,
    JSON.stringify({ userId, menuId }),
    requestParams(data.orderKey)
  );

  check(response, {
    'same-key payment response is 201': (value) => value.status === 201,
    'same-key payment deducts points exactly once': (value) =>
      value.json('data.remainingBalance') === data.expectedRemainingBalance,
  });
}

function readParams() {
  return {
    headers: { Accept: 'application/json' },
    timeout: '5s',
  };
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
