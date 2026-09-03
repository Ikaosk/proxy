'use strict';

// priceStars — цена в Telegram Stars (используется, если PROVIDER_TOKEN не задан).
// priceRub — цена в рублях (используется при оплате через платёжного провайдера).
const PLANS = [
  { id: '1m', title: '1 месяц', days: 30, priceStars: 150, priceRub: 199 },
  { id: '3m', title: '3 месяца', days: 90, priceStars: 400, priceRub: 499 },
  { id: '6m', title: '6 месяцев', days: 180, priceStars: 750, priceRub: 899 },
  { id: '12m', title: '12 месяцев', days: 365, priceStars: 1400, priceRub: 1699 },
];

function findPlan(planId) {
  return PLANS.find((p) => p.id === planId) || null;
}

module.exports = { PLANS, findPlan };
