# Entity-Relationship Model — Ecommerce API

## Diagram

```mermaid
erDiagram
    users {
        UUID id PK
        VARCHAR email UK "NOT NULL"
        VARCHAR firstname "NOT NULL"
        VARCHAR lastname "NOT NULL"
        INTEGER age "NOT NULL"
        VARCHAR password "NOT NULL"
        VARCHAR role "ENUM: USER | ADMIN"
    }

    shopping_cart {
        UUID id PK
        DECIMAL total "NOT NULL"
        BOOLEAN payed "NOT NULL"
        UUID user_id FK
    }

    products {
        UUID id PK
        VARCHAR name "NOT NULL"
        VARCHAR desc "NOT NULL"
        DECIMAL price "NOT NULL"
        VARCHAR image_path "NOT NULL"
        VARCHAR category "ENUM: PHONE | COMPUTER"
    }

    products_shopping_cart {
        UUID id PK
        INTEGER quantity "NOT NULL"
        UUID shopping_cart_id FK
        UUID product_id FK
    }

    users ||--o{ shopping_cart : "has"
    shopping_cart ||--o{ products_shopping_cart : "contains"
    products ||--o{ products_shopping_cart : "referenced by"
```

## Tables

### `users`
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, generated |
| `email` | VARCHAR | UNIQUE, NOT NULL |
| `firstname` | VARCHAR | NOT NULL |
| `lastname` | VARCHAR | NOT NULL |
| `age` | INTEGER | NOT NULL |
| `password` | VARCHAR | NOT NULL |
| `role` | VARCHAR | NOT NULL, ENUM(`USER`, `ADMIN`) |

### `shopping_cart`
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, generated |
| `total` | DECIMAL | NOT NULL |
| `payed` | BOOLEAN | NOT NULL |
| `user_id` | UUID | FK → `users.id`, NOT NULL |

### `products`
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, generated |
| `name` | VARCHAR | NOT NULL |
| `desc` | VARCHAR | NOT NULL |
| `price` | DECIMAL | NOT NULL |
| `image_path` | VARCHAR | NOT NULL |
| `category` | VARCHAR | NOT NULL, ENUM(`PHONE`, `COMPUTER`) |

### `products_shopping_cart`
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, generated |
| `quantity` | INTEGER | NOT NULL |
| `shopping_cart_id` | UUID | FK → `shopping_cart.id`, NOT NULL |
| `product_id` | UUID | FK → `products.id`, NOT NULL |

## Relationships

| Relationship | Cardinality | Description |
|---|---|---|
| `users` → `shopping_cart` | One-to-Many | A user can have multiple shopping carts |
| `shopping_cart` → `products_shopping_cart` | One-to-Many | A cart contains multiple line items |
| `products` → `products_shopping_cart` | One-to-Many | A product can appear in multiple carts |

> `products_shopping_cart` is a join table between `shopping_cart` and `products`, adding `quantity` as an extra attribute.
