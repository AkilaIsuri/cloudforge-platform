import { useState } from 'react';
import { api, imageFor, money, tintFor } from '../api.js';
import { CartIcon, CheckIcon, ShieldIcon } from '../icons.jsx';

export default function Cart({ cart, user, onQuantity, onClear, onNavigate }) {
  const [placing, setPlacing] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  const itemCount = cart.reduce((n, item) => n + item.quantity, 0);
  const total = cart.reduce((sum, item) => sum + item.product.price * item.quantity, 0);

  const checkout = async () => {
    setPlacing(true);
    setError('');
    setResult(null);
    try {
      const order = await api.placeOrder({
        userId: user.id,
        paymentMethod: 'CARD',
        items: cart.map((item) => ({ productId: item.product.id, quantity: item.quantity })),
      });
      setResult(order);
      if (order.status === 'PAID') onClear();
    } catch (e) {
      setError(e.message);
    } finally {
      setPlacing(false);
    }
  };

  if (result?.status === 'PAID' && cart.length === 0) {
    return (
      <section className="state-card">
        <span className="state-icon success"><CheckIcon width={30} height={30} /></span>
        <h1>Order confirmed</h1>
        <p className="muted">
          Order #{result.id} for {money(result.totalAmount)} is paid. Thanks for shopping with VoltCart!
        </p>
        <div className="state-actions">
          <button className="btn primary" onClick={() => onNavigate('orders')}>View my orders</button>
          <button className="btn ghost" onClick={() => onNavigate('shop')}>Keep shopping</button>
        </div>
      </section>
    );
  }

  if (cart.length === 0) {
    return (
      <section className="state-card">
        <span className="state-icon"><CartIcon width={30} height={30} /></span>
        <h1>Your cart is empty</h1>
        <p className="muted">Find something you love in the shop.</p>
        <div className="state-actions">
          <button className="btn primary" onClick={() => onNavigate('shop')}>Start shopping</button>
        </div>
      </section>
    );
  }

  return (
    <section>
      <div className="page-head">
        <h1>Your cart</h1>
        <p className="muted">{itemCount} {itemCount === 1 ? 'item' : 'items'}</p>
      </div>

      {result?.status === 'PAYMENT_FAILED' && (
        <div className="alert error">
          Payment declined for order #{result.id}: {result.failureReason}. Nothing was charged and your
          cart is unchanged.
        </div>
      )}
      {error && <div className="alert error">{error}</div>}

      <div className="cart-layout">
        <ul className="cart-items">
          {cart.map(({ product, quantity }) => (
            <li key={product.id} className="cart-item">
              <div className={`thumb-sm tint-${tintFor(product.category)}`}>
                <img src={imageFor(product)} alt="" />
              </div>
              <div className="cart-item-info">
                <strong>{product.name}</strong>
                <span className="muted small">{money(product.price)} each</span>
                <button className="link small" onClick={() => onQuantity(product.id, 0)}>Remove</button>
              </div>
              <div className="qty">
                <button onClick={() => onQuantity(product.id, quantity - 1)} aria-label={`Decrease ${product.name}`}>−</button>
                <span>{quantity}</span>
                <button
                  onClick={() => onQuantity(product.id, quantity + 1)}
                  disabled={quantity >= product.stock}
                  aria-label={`Increase ${product.name}`}
                >
                  +
                </button>
              </div>
              <strong className="line-total">{money(product.price * quantity)}</strong>
            </li>
          ))}
        </ul>

        <aside className="summary-card">
          <h2>Order summary</h2>
          <div className="summary-row"><span>Subtotal</span><span>{money(total)}</span></div>
          <div className="summary-row"><span>Delivery</span><span className="free">Free</span></div>
          <div className="summary-row total"><span>Total</span><strong>{money(total)}</strong></div>

          {user ? (
            <button className="btn primary block" onClick={checkout} disabled={placing}>
              {placing ? 'Processing payment…' : `Pay ${money(total)}`}
            </button>
          ) : (
            <button className="btn primary block" onClick={() => onNavigate('account')}>
              Sign in to check out
            </button>
          )}

          <p className="secure"><ShieldIcon width={16} height={16} /> Secure checkout</p>
          <p className="demo-note">Demo: payments over $5,000 are declined by the mock payment service.</p>
          <button className="link small center" onClick={onClear}>Empty cart</button>
        </aside>
      </div>
    </section>
  );
}
