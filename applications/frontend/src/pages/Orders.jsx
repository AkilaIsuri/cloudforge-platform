import { useEffect, useState } from 'react';
import { api, imageFor, money } from '../api.js';
import { BoxIcon, UserIcon } from '../icons.jsx';

const STATUS = {
  PAID: { label: 'Paid', className: 'badge success' },
  PAYMENT_FAILED: { label: 'Payment failed', className: 'badge error' },
  PENDING: { label: 'Pending', className: 'badge' },
};

export default function Orders({ user, onNavigate }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(Boolean(user));
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user) return;
    api.getOrders(user.id)
      .then(setOrders)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [user]);

  if (!user) {
    return (
      <section className="state-card">
        <span className="state-icon"><UserIcon width={30} height={30} /></span>
        <h1>Sign in to see your orders</h1>
        <p className="muted">Your order history appears here once you’re signed in.</p>
        <div className="state-actions">
          <button className="btn primary" onClick={() => onNavigate('account')}>Sign in</button>
        </div>
      </section>
    );
  }

  if (!loading && !error && orders.length === 0) {
    return (
      <section className="state-card">
        <span className="state-icon"><BoxIcon width={30} height={30} /></span>
        <h1>No orders yet</h1>
        <p className="muted">When you place an order it will show up here.</p>
        <div className="state-actions">
          <button className="btn primary" onClick={() => onNavigate('shop')}>Browse products</button>
        </div>
      </section>
    );
  }

  return (
    <section className="narrow">
      <div className="page-head">
        <h1>Your orders</h1>
        {!loading && <p className="muted">{orders.length} {orders.length === 1 ? 'order' : 'orders'}</p>}
      </div>

      {loading && <div className="card skeleton tall" />}
      {error && <div className="alert error">{error}</div>}

      <ul className="orders">
        {orders.map((order) => {
          const status = STATUS[order.status] || { label: order.status, className: 'badge' };
          return (
            <li key={order.id} className="order-card">
              <div className="order-head">
                <div>
                  <strong>Order #{order.id}</strong>
                  <span className="muted small">
                    {new Date(order.createdAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
                  </span>
                </div>
                <span className={status.className}>{status.label}</span>
                <strong className="order-total">{money(order.totalAmount)}</strong>
              </div>

              <ul className="order-items">
                {order.items.map((item) => (
                  <li key={item.id}>
                    <img src={imageFor({ productName: item.productName })} alt="" />
                    <span>{item.quantity} × {item.productName}</span>
                    <span className="muted">{money(item.unitPrice * item.quantity)}</span>
                  </li>
                ))}
              </ul>

              {order.failureReason && <p className="order-failure">{order.failureReason}</p>}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
