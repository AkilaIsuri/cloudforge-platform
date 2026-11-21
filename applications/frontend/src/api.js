// All calls go through the API gateway at /api (nginx or the Vite dev server forwards it).
const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

async function request(path, options = {}) {
  const response = await fetch(BASE_URL + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      message = body.message || body.error || message;
    } catch {
      // response had no JSON body
    }
    throw new Error(message);
  }

  return response.status === 204 ? null : response.json();
}

// IDs come from browser storage, which a user can edit. Only a positive whole number may ever be
// placed into a request URL, so the value can't change which endpoint or host is called.
function toId(value) {
  const id = Number(value);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('Invalid id');
  }
  return id;
}

export const api = {
  getProducts: () => request('/products'),

  getUsers: () => request('/users'),
  createUser: (user) => request('/users', { method: 'POST', body: JSON.stringify(user) }),

  placeOrder: (order) => request('/orders', { method: 'POST', body: JSON.stringify(order) }),
  getOrders: (userId) =>
    request(`/orders?${new URLSearchParams({ userId: String(toId(userId)) })}`),
};

export const money = (value) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);

// Product images live in the frontend (public/images/products). product-service stores the
// path in imageUrl; the name/category maps are a fallback (e.g. for order line items).
const IMAGE_BY_NAME = {
  'Wireless Headphones': 'headphones',
  'Bluetooth Speaker': 'speaker',
  'Mechanical Keyboard': 'keyboard',
  'USB-C Hub': 'usb-hub',
  'HD Webcam': 'webcam',
  '27" 4K Monitor': 'monitor',
  'Laptop Pro 14': 'laptop',
  Smartwatch: 'smartwatch',
};

const IMAGE_BY_CATEGORY = {
  Audio: 'headphones',
  Accessories: 'keyboard',
  Displays: 'monitor',
  Computers: 'laptop',
  Wearables: 'smartwatch',
};

export function imageFor({ imageUrl, name, productName, category } = {}) {
  if (imageUrl) return imageUrl;
  const key = IMAGE_BY_NAME[name || productName] || IMAGE_BY_CATEGORY[category];
  return `/images/products/${key || 'placeholder'}.svg`;
}

const TINT_BY_CATEGORY = {
  Audio: 'violet',
  Accessories: 'sky',
  Displays: 'indigo',
  Computers: 'slate',
  Wearables: 'amber',
};

export const tintFor = (category) => TINT_BY_CATEGORY[category] || 'indigo';
