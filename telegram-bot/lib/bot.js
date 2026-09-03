'use strict';

const { Telegraf, Markup } = require('telegraf');
const { PLANS, findPlan } = require('./plans');
const store = require('./store');

const BTN_CABINET = '👤 Личный кабинет';
const BTN_SUBSCRIPTIONS = '📋 Управление подписками';
const BTN_BUY = '💳 Купить подписку';

const mainMenu = Markup.keyboard([[BTN_CABINET, BTN_SUBSCRIPTIONS], [BTN_BUY]]).resize();

function formatDate(iso) {
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatPrice(plan, currency) {
  return currency === 'XTR' ? `${plan.priceStars} ⭐` : `${plan.priceRub} ₽`;
}

function plansKeyboard(currency) {
  return Markup.inlineKeyboard(
    PLANS.map((p) => [Markup.button.callback(`${p.title} — ${formatPrice(p, currency)}`, `buy_plan:${p.id}`)])
  );
}

function createBot(config) {
  const bot = new Telegraf(config.botToken);
  const loadStore = () => store.load(config.storePath);
  const saveStore = (data) => store.save(config.storePath, data);

  function touchUser(ctx) {
    const data = loadStore();
    const user = store.getOrCreateUser(data, ctx.from.id, {
      username: ctx.from.username,
      firstName: ctx.from.first_name,
    });
    saveStore(data);
    return user;
  }

  bot.start((ctx) => {
    touchUser(ctx);
    ctx.reply(
      'Добро пожаловать! Это бот для управления доступом к прокси/VPN.\n\n' +
        `${BTN_CABINET} — статус подписки и данные для подключения\n` +
        `${BTN_SUBSCRIPTIONS} — история и продление подписок\n` +
        `${BTN_BUY} — оформить новую подписку`,
      mainMenu
    );
  });

  bot.hears(BTN_CABINET, (ctx) => {
    const user = touchUser(ctx);
    const sub = store.getActiveSubscription(user);

    const lines = [
      `ID: \`${user.telegramId}\``,
      `Регистрация: ${formatDate(user.createdAt)}`,
      '',
    ];

    if (sub) {
      const plan = findPlan(sub.planId);
      lines.push(`Подписка: ✅ активна (${plan ? plan.title : sub.planId})`);
      lines.push(`Действует до: ${formatDate(sub.expiresAt)}`);
      lines.push('');
      lines.push('Данные для подключения:');
      lines.push(`Логин: \`${user.proxyLogin}\``);
      lines.push(`Пароль: \`${user.proxyPassword}\``);
      lines.push(`HTTP(S)-прокси: \`${config.proxyHost}:${config.proxyHttpPort}\``);
      lines.push(`SOCKS5: \`${config.proxyHost}:${config.proxySocksPort}\``);
      lines.push('');
      lines.push('Быстрая проверка через curl:');
      lines.push(
        `\`curl -x http://${user.proxyLogin}:${user.proxyPassword}@${config.proxyHost}:${config.proxyHttpPort} https://example.com\``
      );
    } else {
      lines.push('Подписка не активна.');
      lines.push(`Оформите её в разделе «${BTN_BUY}».`);
    }

    ctx.reply(lines.join('\n'), { parse_mode: 'Markdown', ...mainMenu });
  });

  bot.hears(BTN_SUBSCRIPTIONS, (ctx) => {
    const user = touchUser(ctx);

    if (user.subscriptions.length === 0) {
      ctx.reply('У вас пока нет подписок.', Markup.inlineKeyboard([[Markup.button.callback('💳 Купить', 'buy_menu')]]));
      return;
    }

    const now = Date.now();
    const lines = user.subscriptions
      .slice()
      .reverse()
      .map((s, i) => {
        const plan = findPlan(s.planId);
        const active = s.status === 'active' && new Date(s.expiresAt).getTime() > now;
        return `${i + 1}. ${plan ? plan.title : s.planId} — до ${formatDate(s.expiresAt)} ${active ? '✅ активна' : '⏹ истекла'}`;
      });

    ctx.reply(lines.join('\n'), Markup.inlineKeyboard([[Markup.button.callback('➕ Продлить / купить', 'buy_menu')]]));
  });

  bot.hears(BTN_BUY, (ctx) => {
    touchUser(ctx);
    ctx.reply('Выберите тариф:', plansKeyboard(config.currency));
  });

  bot.action('buy_menu', async (ctx) => {
    await ctx.answerCbQuery();
    await ctx.reply('Выберите тариф:', plansKeyboard(config.currency));
  });

  bot.action(/^buy_plan:(.+)$/, async (ctx) => {
    const plan = findPlan(ctx.match[1]);
    if (!plan) {
      await ctx.answerCbQuery('Тариф не найден');
      return;
    }
    await ctx.answerCbQuery();

    const isStars = config.currency === 'XTR';
    const amount = isStars ? plan.priceStars : plan.priceRub * 100; // RUB — в копейках

    await ctx.replyWithInvoice({
      title: `Подписка: ${plan.title}`,
      description: `Доступ к прокси/VPN на ${plan.days} дней`,
      payload: JSON.stringify({ planId: plan.id }),
      provider_token: isStars ? '' : config.providerToken,
      currency: config.currency,
      prices: [{ label: plan.title, amount }],
    });
  });

  bot.on('pre_checkout_query', (ctx) => ctx.answerPreCheckoutQuery(true));

  bot.on('message', async (ctx, next) => {
    const payment = ctx.message && ctx.message.successful_payment;
    if (!payment) return next();

    let planId = null;
    try {
      planId = JSON.parse(payment.invoice_payload).planId;
    } catch {
      // не удалось распарсить payload — обработаем ниже как "тариф не найден"
    }
    const plan = findPlan(planId);
    if (!plan) {
      await ctx.reply('Платёж получен, но тариф не распознан — обратитесь в поддержку.', mainMenu);
      return;
    }

    const data = loadStore();
    const user = store.getOrCreateUser(data, ctx.from.id, {
      username: ctx.from.username,
      firstName: ctx.from.first_name,
    });
    const subscription = store.addSubscription(user, plan);
    store.addPayment(user, {
      planId: plan.id,
      amount: payment.total_amount,
      currency: payment.currency,
      telegramChargeId: payment.telegram_payment_charge_id,
      providerChargeId: payment.provider_payment_charge_id,
    });
    saveStore(data);

    await ctx.reply(
      `Оплата получена ✅\nТариф «${plan.title}» активен до ${formatDate(subscription.expiresAt)}.\n\n` +
        `Данные для подключения — в разделе «${BTN_CABINET}».`,
      mainMenu
    );
  });

  bot.command('grant', (ctx) => {
    if (!config.adminIds.includes(ctx.from.id)) return;
    const parts = ctx.message.text.trim().split(/\s+/);
    const [, telegramIdStr, planId] = parts;
    const plan = findPlan(planId);
    if (!telegramIdStr || !plan) {
      ctx.reply('Использование: /grant <telegram_id> <planId>\nДоступные тарифы: ' + PLANS.map((p) => p.id).join(', '));
      return;
    }
    const data = loadStore();
    const user = store.getOrCreateUser(data, Number(telegramIdStr), {});
    const subscription = store.addSubscription(user, plan);
    store.addPayment(user, { planId: plan.id, amount: 0, currency: 'MANUAL', telegramChargeId: null, providerChargeId: null });
    saveStore(data);
    ctx.reply(`Выдано: ${plan.title} для ${telegramIdStr}, до ${formatDate(subscription.expiresAt)}.`);
  });

  bot.catch((err, ctx) => {
    console.error(new Date().toISOString(), `Ошибка при обработке update ${ctx.updateType}:`, err);
  });

  return bot;
}

module.exports = createBot;
