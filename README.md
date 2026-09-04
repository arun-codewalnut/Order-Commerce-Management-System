# bad-commerce-api

Order & Commerce Management System backend service.

> This project is intentionally a legacy-style codebase used for refactoring and engineering-practice training.

---

## 1. Overview

`bad-commerce-api` is a monolithic Spring Boot backend service for managing e-commerce operations. It provides core functionality across:

- **Customers**: Customer profile management and registration
- **Products**: Catalog management, product lookup, category browsing, and search
- **Inventory**: Product stock level tracking and reservations
- **Orders**: Multi-item order creation, pricing calculation, status management, and cancellation
- **Payments**: Payment processing and transaction records
- **Notifications**: Order status notifications and dispatch tracking

---

## 2. Technology Stack & Requirements

- **Java**: Java 21 LTS
- **Build Tool**: Apache Maven 3.9+
- **Framework**: Spring Boot 3.3.x (Spring Web, Spring Data JPA, Spring Security, Validation, Cache, Actuator)
- **Database**: PostgreSQL 16 (H2 embedded for automated tests)
- **API Documentation**: Springdoc OpenAPI / Swagger UI
- **Containers**: Docker & Docker Compose

---

## 3. Database Configuration

The application connects to a PostgreSQL database. By default, connection parameters are:

- **URL**: `jdbc:postgresql://localhost:5432/bad_commerce`
- **Username**: `postgres`
- **Password**: `postgres`

These can be overridden using environment variables:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Schema management is handled via JPA auto-update (`spring.jpa.hibernate.ddl-auto=update`).

---

## 4. Running the Application

### Option A: Using Docker Compose (Recommended)

To launch both PostgreSQL and the API service in containers:

```bash
docker compose up --build
```

The service will be accessible at `http://localhost:8080`.

### Option B: Local Development with Maven

1. Start PostgreSQL locally (e.g. via Docker):
   ```bash
   docker run --name bad-commerce-postgres -e POSTGRES_DB=bad_commerce -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16-alpine
   ```

2. Compile and run the Spring Boot application:
   ```bash
   mvn clean spring-boot:run
   ```

### Option C: Running Automated Tests

Automated tests execute against an embedded in-memory H2 database:

```bash
mvn clean test
```

---

## 5. API Documentation & Endpoints

Once the application is running, interactive API documentation is available at:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/api-docs`
- **Actuator Health**: `http://localhost:8080/actuator/health`

### Key REST Endpoints

| Area | Method | Endpoint | Description |
|---|---|---|---|
| **Customer** | `POST` | `/api/customers` | Register a new customer |
| | `GET` | `/api/customers/{id}` | Get customer by ID |
| | `GET` | `/api/customers` | List all customers |
| | `PUT` | `/api/customers/{id}` | Update customer |
| | `DELETE` | `/api/customers/{id}` | Remove customer |
| **Product** | `POST` | `/api/products` | Create a product |
| | `GET` | `/api/products/{id}` | Get product by ID |
| | `GET` | `/api/products` | List all products (supports `sortBy` param) |
| | `GET` | `/api/products/search?keyword=...` | Search products by name |
| | `PUT` | `/api/products/{id}` | Update product details |
| | `DELETE` | `/api/products/{id}` | Delete product |
| **Inventory** | `GET` | `/api/inventory/{productId}` | Check stock levels |
| | `PUT` | `/api/inventory/{productId}` | Update stock quantities |
| **Order** | `POST` | `/api/orders` | Place a new order |
| | `GET` | `/api/orders/{id}` | Retrieve order details (supports `customerId` param) |
| | `GET` | `/api/orders` | List all orders with items |
| | `POST` | `/api/orders/{id}/cancel` | Cancel an order |
| **Payment** | `POST` | `/api/payments` | Process a payment |
| | `GET` | `/api/payments/{id}` | Get payment by ID |
| | `GET` | `/api/payments?orderId=...` | List payments for an order |
| **Notification** | `GET` | `/api/notifications` | List notifications |

---

## 6. Authentication

The application uses HTTP Basic Authentication with default seed credentials:

- **Customer**: `customer@example.com` / `password123`
- **Admin**: `admin@example.com` / `admin123`

---

## 7. Sample API Requests

### 1. Retrieve All Products
```bash
curl -u customer@example.com:password123 http://localhost:8080/api/products
```

### 2. Search Products
```bash
curl -u customer@example.com:password123 "http://localhost:8080/api/products/search?keyword=Headphones"
```

### 3. Place an Order
```bash
curl -X POST http://localhost:8080/api/orders \
  -u customer@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "shippingCountry": "US",
    "items": [
      { "productId": 1, "quantity": 1 },
      { "productId": 4, "quantity": 2 }
    ]
  }'
```

### 4. Fetch Order by ID
```bash
curl -u customer@example.com:password123 http://localhost:8080/api/orders/1
```

### 5. Process Payment
```bash
curl -X POST http://localhost:8080/api/payments \
  -u customer@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 2,
    "customerId": 2,
    "amount": 89.50,
    "paymentMethod": "CREDIT_CARD"
  }'
```

