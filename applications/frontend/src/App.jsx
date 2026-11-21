import { useEffect, useRef, useState } from 'react';
import Shop from './pages/Shop.jsx';
import Cart from './pages/Cart.jsx';
import Orders from './pages/Orders.jsx';
import Account from './pages/Account.jsx';
import { CartIcon, CheckIcon, Logo } from './icons.jsx';

function load(key, fallback) {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
}

function save(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // storage unavailable (private mode) – the app still works for this session
  }
}

export default function App() {
  const [page, setPage] = useState('shop');
  const [cart, setCart] = useState(() => load('voltcart.cart', []));
  const [user, setUser] = useState(() => load('voltcart.user', null));
  const [toast, setToast] = useState('');
  const toastTimer = useRef();

  useEffect(() => { save('voltcart.cart', cart); }, [cart]);
  useEffect(() => { save('voltcart.user', user); }, [user]);

  const go = (target) => {
    setPage(target);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const notify = (message) => {
    setToast(message);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(''), 2400);
  };

  const addToCart = (product) => {
    setCart((items) => {
      const existing = items.find((item) => item.product.id === product.id);
      if (existing) {
        return items.map((item) =>
          item.product.id === product.id
            ? { ...item, quantity: Math.min(item.quantity + 1, product.stock) }
            : item
        );
      }
      return [...items, { product, quantity: 1 }];
    });
    notify(`${product.name} added to your cart`);
  };

  const setQuantity = (productId, quantity) =>
    setCart((items) =>
      quantity <= 0
        ? items.filter((item) => item.product.id !== productId)
        : items.map((item) => (item.product.id === productId ? { ...item, quantity } : item))
    );

  const cartCount = cart.reduce((count, item) => count + item.quantity, 0);

  const links = [
    { id: 'shop', label: 'Shop' },
    { id: 'orders', label: 'Orders' },
    { id: 'account', label: user ? `Hi, ${user.firstName}` : 'Sign in' },
  ];

  return (
    <div className="app">
      <header className="header">
        <div className="header-inner">
          <button className="brand" onClick={() => go('shop')} aria-label="VoltCart home">
            <Logo />
            <span className="brand-name">Volt<span>Cart</span></span>
          </button>

          <nav className="nav">
            {links.map((link) => (
              <button
                key={link.id}
                className={page === link.id ? 'nav-link active' : 'nav-link'}
                onClick={() => go(link.id)}
              >
                {link.label}
              </button>
            ))}
          </nav>

          <button className={page === 'cart' ? 'cart-button active' : 'cart-button'} onClick={() => go('cart')}>
            <CartIcon />
            <span className="cart-label">Cart</span>
            {cartCount > 0 && <span className="cart-count">{cartCount}</span>}
          </button>
        </div>
      </header>

      <main className="content">
        {page === 'shop' && <Shop onAdd={addToCart} cart={cart} />}
        {page === 'cart' && (
          <Cart cart={cart} user={user} onQuantity={setQuantity} onClear={() => setCart([])} onNavigate={go} />
        )}
        {page === 'orders' && <Orders user={user} onNavigate={go} />}
        {page === 'account' && <Account user={user} onUser={setUser} onNavigate={go} />}
      </main>

      <footer className="footer">
        <div className="footer-inner">
          <div className="footer-brand">
            <Logo size={28} />
            <div>
              <strong>VoltCart</strong>
              <p>Electronics that power your day.</p>
            </div>
          </div>
          <p className="footer-note">
            Demo store running on the CloudForge platform. No real payments are taken.
          </p>
        </div>
      </footer>

      <div className={toast ? 'toast show' : 'toast'} role="status" aria-live="polite">
        <CheckIcon width={18} height={18} /> {toast}
      </div>
    </div>
  );
}
