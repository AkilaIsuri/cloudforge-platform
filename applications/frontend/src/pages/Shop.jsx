import { useEffect, useRef, useState } from 'react';
import { api, imageFor, money, tintFor } from '../api.js';
import { BoltIcon, PlusIcon, ReturnIcon, SearchIcon, ShieldIcon, TruckIcon } from '../icons.jsx';

const PERKS = [
  { icon: TruckIcon, title: 'Fast delivery', text: 'Free on orders over $50' },
  { icon: ShieldIcon, title: 'Secure checkout', text: 'Your payment is protected' },
  { icon: ReturnIcon, title: '30-day returns', text: 'Change your mind? No problem' },
];

export default function Shop({ onAdd, cart }) {
  const [products, setProducts] = useState([]);
  const [category, setCategory] = useState('All');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const catalogRef = useRef(null);

  const loadProducts = () => {
    setLoading(true);
    setError('');
    api.getProducts()
      .then(setProducts)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadProducts(); }, []);

  const categories = ['All', ...new Set(products.map((p) => p.category).filter(Boolean))];
  const search = query.trim().toLowerCase();
  const visible = products.filter(
    (p) =>
      (category === 'All' || p.category === category) &&
      (!search || `${p.name} ${p.description} ${p.category}`.toLowerCase().includes(search))
  );
  const inCart = (id) => cart.find((item) => item.product.id === id)?.quantity || 0;

  return (
    <>
      <section className="hero">
        <div className="hero-text">
          <span className="eyebrow"><BoltIcon width={16} height={16} /> New season tech</span>
          <h1>Power up your setup.</h1>
          <p>
            Headphones, keyboards, displays and more. Hand-picked gear that keeps up with you,
            delivered fast.
          </p>
          <div className="hero-actions">
            <button
              className="btn volt"
              onClick={() => catalogRef.current?.scrollIntoView({ behavior: 'smooth' })}
            >
              Shop the collection
            </button>
            <span className="hero-note">{products.length || 8} products in stock</span>
          </div>
        </div>
        <div className="hero-art" aria-hidden="true">
          <div className="hero-glow" />
          <img className="art art-laptop" src="/images/products/laptop.svg" alt="" />
          <img className="art art-headphones" src="/images/products/headphones.svg" alt="" />
          <img className="art art-watch" src="/images/products/smartwatch.svg" alt="" />
        </div>
      </section>

      <section className="perks">
        {PERKS.map(({ icon: Icon, title, text }) => (
          <div key={title} className="perk">
            <span className="perk-icon"><Icon /></span>
            <div>
              <strong>{title}</strong>
              <span>{text}</span>
            </div>
          </div>
        ))}
      </section>

      <section className="catalog" ref={catalogRef}>
        <div className="catalog-head">
          <div>
            <h2>Featured products</h2>
            <p className="muted">{loading ? 'Loading…' : `${visible.length} of ${products.length} items`}</p>
          </div>
          <label className="search">
            <SearchIcon width={18} height={18} />
            <input
              type="search"
              placeholder="Search products"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Search products"
            />
          </label>
        </div>

        <div className="chips">
          {categories.map((c) => (
            <button key={c} className={c === category ? 'chip active' : 'chip'} onClick={() => setCategory(c)}>
              {c}
            </button>
          ))}
        </div>

        {error && (
          <div className="alert error">
            Could not load products: {error}
            <button className="btn ghost sm" onClick={loadProducts}>Try again</button>
          </div>
        )}

        <div className="grid">
          {loading &&
            Array.from({ length: 8 }, (_, i) => <div key={i} className="card skeleton" />)}

          {!loading &&
            visible.map((product) => {
              const quantityInCart = inCart(product.id);
              const remaining = product.stock - quantityInCart;
              return (
                <article key={product.id} className="card">
                  <div className={`thumb tint-${tintFor(product.category)}`}>
                    <img src={imageFor(product)} alt={product.name} loading="lazy" />
                    {product.stock === 0 && <span className="stock-badge out">Sold out</span>}
                    {product.stock > 0 && product.stock <= 10 && (
                      <span className="stock-badge low">Only {product.stock} left</span>
                    )}
                  </div>
                  <div className="card-body">
                    <span className="card-category">{product.category}</span>
                    <h3>{product.name}</h3>
                    <p className="card-desc">{product.description}</p>
                    <div className="card-footer">
                      <span className="price">{money(product.price)}</span>
                      <button className="btn primary sm" disabled={remaining <= 0} onClick={() => onAdd(product)}>
                        <PlusIcon width={16} height={16} />
                        {product.stock === 0 ? 'Sold out' : remaining <= 0 ? 'Max in cart' : 'Add'}
                      </button>
                    </div>
                    {quantityInCart > 0 && <span className="in-cart">{quantityInCart} in your cart</span>}
                  </div>
                </article>
              );
            })}
        </div>

        {!loading && !error && visible.length === 0 && (
          <div className="empty">
            <p>No products match “{query}”.</p>
            <button className="btn ghost" onClick={() => { setQuery(''); setCategory('All'); }}>Clear filters</button>
          </div>
        )}
      </section>
    </>
  );
}
