# ReClaim Portal - API Specification

## Authentication

All API endpoints (except auth) require a valid JWT. Two delivery methods:
- **Header**: `Authorization: Bearer <accessToken>`
- **Cookie**: `accessToken` HttpOnly cookie (set by login/refresh)

CSRF-protected endpoints require the `X-XSRF-TOKEN` header with the value from the `XSRF-TOKEN` cookie.

### Error Response Format

```json
{
  "timestamp": "2026-04-10T22:00:00.000Z",
  "status": 409,
  "error": "Conflict",
  "message": "Business rule violation description"
}
```

Validation errors include an `errors` array:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": ["username: must not be blank", "password: must not be blank"]
}
```

---

## 1. Authentication (`/api/auth`)

CSRF-exempt. Refresh and logout validate `Origin` header.

### POST `/api/auth/login`

Login and receive JWT tokens.

**Request**:
```json
{
  "username": "admin",
  "password": "password123"
}
```

**Response** `200 OK`:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": null,
  "forcePasswordReset": true
}
```

Sets cookies:
- `accessToken` (HttpOnly, SameSite=Strict, path=/, max-age=1800)
- `refreshToken` (HttpOnly, SameSite=Strict, path=/api/auth, max-age=604800)

**Errors**:
- `409` - Invalid username or password
- `409` - Account is disabled
- `409` - Account is locked

---

### POST `/api/auth/refresh`

Refresh access token using the refresh cookie.

**Request**: No body. `refreshToken` cookie is read automatically.

**Response** `200 OK`:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": null,
  "forcePasswordReset": false
}
```

**Errors**:
- `400` - No refresh cookie
- `409` - Invalid/revoked/expired refresh token
- `409` - Account is disabled or locked

---

### POST `/api/auth/logout`

Revoke refresh token and clear cookies.

**Request**: No body.

**Response** `200 OK`: Empty body.

---

### POST `/api/auth/change-password`

Change the authenticated user's password. Requires CSRF token.

**Request**:
```json
{
  "oldPassword": "currentPassword",
  "newPassword": "NewSecurePass1!"
}
```

**Response** `200 OK`: Empty body.

**Password rules**: 12+ chars, uppercase, lowercase, digit, special character.

**Errors**:
- `409` - Current password is incorrect
- `409` - Password strength requirements not met
- `401` - Not authenticated

---

## 2. Catalog (`/api/catalog`)

### GET `/api/catalog/search`

Search recycling items with filters and ranking.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `keyword` | string | Search term (title/description) |
| `category` | string | Category filter |
| `condition` | string | Condition filter (NEW, LIKE_NEW, GOOD, FAIR, POOR) |
| `minPrice` | decimal | Minimum price |
| `maxPrice` | decimal | Maximum price |

**Response** `200 OK`:
```json
{
  "items": [
    {
      "id": 1,
      "title": "Apple iPhone 12 64GB Space Gray",
      "normalizedTitle": "apple iphone 12 64gb space gray",
      "description": "Fully functional...",
      "category": "Electronics",
      "itemCondition": "GOOD",
      "price": 229.99,
      "currency": "USD",
      "sellerId": 4,
      "active": true,
      "createdAt": "2026-04-10T22:00:00",
      "updatedAt": "2026-04-10T22:00:00"
    }
  ],
  "searchLogId": 42
}
```

---

### GET `/api/catalog/{id}`

Get a single catalog item.

**Response** `200 OK`: `RecyclingItem` object (same shape as search result item).

**Errors**: `404` - Item not found.

---

### POST `/api/catalog/click`

Log a search result click for analytics and ranking.

**Request**:
```json
{
  "itemId": 1,
  "searchLogId": 42
}
```

**Response** `200 OK`: Empty body.

---

### POST `/api/catalog/check-duplicate`

Check if an item listing is a duplicate.

**Request**:
```json
{
  "title": "Apple iPhone 12",
  "attributes": "GOOD,Electronics"
}
```

**Response** `200 OK`:
```json
{
  "status": "EXACT_DUPLICATE" | "NEAR_DUPLICATE" | "UNIQUE"
}
```

---

## 3. Orders (`/api/orders`)

### POST `/api/orders`

Create a new order.

**Request**:
```json
{
  "itemIds": [1, 2, 3],
  "appointmentId": 5,
  "appointmentType": "PICKUP"
}
```

**Response** `200 OK`:
```json
{
  "id": 1,
  "userId": 3,
  "appointmentId": 5,
  "orderStatus": "PENDING_CONFIRMATION",
  "appointmentType": "PICKUP",
  "rescheduleCount": 0,
  "totalPrice": 234.99,
  "currency": "USD",
  "version": 0,
  "createdAt": "2026-04-10T22:00:00",
  "updatedAt": "2026-04-10T22:00:00"
}
```

**Errors**:
- `409` - Appointment type mismatch
- `409` - No valid items found
- `409` - No capacity available

---

### GET `/api/orders/my`

Get all orders for the authenticated user.

**Response** `200 OK`: `Order[]`

---

### GET `/api/orders/{id}`

Get order detail with items and operation logs.

**Response** `200 OK`:
```json
{
  "order": { ... },
  "items": [
    {
      "id": 1,
      "orderId": 1,
      "itemId": 3,
      "snapshotTitle": "Apple iPhone 12",
      "snapshotCategory": "Electronics",
      "snapshotCondition": "GOOD",
      "snapshotPrice": 229.99,
      "adjustedCategory": null,
      "adjustedBy": null,
      "adjustedAt": null
    }
  ],
  "logs": [
    {
      "id": 1,
      "orderId": 1,
      "actorId": 3,
      "operation": "ORDER_CREATED",
      "previousStatus": null,
      "newStatus": "PENDING_CONFIRMATION",
      "details": "Order created with 1 item(s)",
      "createdAt": "2026-04-10T22:00:00"
    }
  ]
}
```

---

### PUT `/api/orders/{id}/accept`

Accept a pending order. **Requires**: REVIEWER or ADMIN.

**Response** `200 OK`: Updated `Order` with status `ACCEPTED`.

---

### PUT `/api/orders/{id}/complete`

Complete an accepted order. **Requires**: REVIEWER or ADMIN.

**Response** `200 OK`: Updated `Order` with status `COMPLETED`.

---

### PUT `/api/orders/{id}/cancel`

Cancel an order (owner only).

**Request**:
```json
{
  "reason": "No longer needed"
}
```

**Response** `200 OK`: Order with status `CANCELED` (or `EXCEPTION` if < 1hr to appointment).

---

### PUT `/api/orders/{id}/approve-cancel`

Approve an EXCEPTION cancellation. **Requires**: REVIEWER or ADMIN.

**Request**:
```json
{
  "reason": "Approved by admin"
}
```

**Response** `200 OK`: Order with status `CANCELED`.

---

### PUT `/api/orders/{id}/reschedule`

Reschedule to a new appointment (owner only, max 2 reschedules).

**Request**:
```json
{
  "newAppointmentId": 12
}
```

**Response** `200 OK`: Updated `Order`.

**Errors**: `409` - Max reschedules reached, type mismatch, or invalid status.

---

## 4. Appointments (`/api/appointments`)

### GET `/api/appointments/available`

Get available appointment slots for a date.

**Parameters**:
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `date` | date (YYYY-MM-DD) | yes | Appointment date |
| `type` | string | yes | PICKUP or DROPOFF |

**Response** `200 OK`:
```json
[
  {
    "id": 1,
    "appointmentDate": "2026-04-15",
    "startTime": "10:00",
    "endTime": "10:30",
    "appointmentType": "PICKUP",
    "slotsAvailable": 5,
    "slotsBooked": 2
  }
]
```

**Errors**: `409` - Date outside booking window (2hr min, 14 day max).

---

### POST `/api/appointments/generate`

Generate slots for a date. **Requires**: ADMIN.

**Parameters**: `date` (YYYY-MM-DD)

**Response** `200 OK`: Empty body.

---

## 5. Reviews (`/api/reviews`)

### POST `/api/reviews`

Create a review for a completed order (order owner only).

**Request**:
```json
{
  "orderId": 1,
  "rating": 4,
  "reviewText": "Good condition, as described."
}
```

**Response** `200 OK`: `Review` object.

**Constraints**: Rating 1-5, text max 1000 chars, one review per order, order must be COMPLETED.

---

### POST `/api/reviews/{id}/images`

Upload an image to a review (max 5 per review).

**Request**: `multipart/form-data` with `file` part (JPG/PNG, max 3MB).

**Response** `200 OK`:
```json
{
  "id": 1,
  "reviewId": 5,
  "fileName": "photo.jpg",
  "filePath": "reviews/a1b2c3d4.jpg",
  "contentType": "image/jpeg",
  "fileSize": 245760,
  "checksum": "e3b0c44298fc...",
  "displayOrder": 1,
  "uploadedAt": "2026-04-10T22:00:00"
}
```

---

### GET `/api/reviews/order/{orderId}`

Get review and images for an order.

**Response** `200 OK`:
```json
{
  "review": { ... },
  "images": [ ... ]
}
```

---

### GET `/api/reviews/my`

Get all reviews by the authenticated user.

**Response** `200 OK`: `Review[]`

---

## 6. Contracts (`/api/contracts`)

### POST `/api/contracts/templates`

Create a contract template. **Requires**: ADMIN.

**Request**:
```json
{
  "name": "Recycling Agreement",
  "description": "Standard recycling contract"
}
```

**Response** `200 OK`: `ContractTemplate` object.

---

### POST `/api/contracts/templates/{id}/versions`

Create a template version. **Requires**: ADMIN.

**Request**:
```json
{
  "content": "This agreement between {{party_name}} and ReClaim...",
  "changeNotes": "Initial version"
}
```

**Response** `200 OK`: `ContractTemplateVersion` object.

---

### POST `/api/contracts/templates/versions/{id}/fields`

Add a clause field to a template version. **Requires**: ADMIN.

**Request**:
```json
{
  "fieldName": "party_name",
  "fieldType": "TEXT",
  "fieldLabel": "Party Name",
  "required": true,
  "defaultValue": "",
  "displayOrder": 1
}
```

**Response** `200 OK`: `ContractClauseField` object.

---

### GET `/api/contracts/templates`

List active templates. **Response** `200 OK`: `ContractTemplate[]`

### GET `/api/contracts/templates/{id}/versions`

List template versions (newest first). **Response** `200 OK`: `ContractTemplateVersion[]`

### GET `/api/contracts/templates/versions/{id}/fields`

List clause fields for a version. **Response** `200 OK`: `ContractClauseField[]`

---

### POST `/api/contracts`

Initiate a contract from an accepted order. **Requires**: REVIEWER or ADMIN.

**Request**:
```json
{
  "orderId": 1,
  "templateVersionId": 3,
  "fieldValues": "{\"party_name\": \"John Doe\"}",
  "startDate": "2026-04-15",
  "endDate": "2026-07-15"
}
```

**Response** `200 OK`: `ContractInstance` with status `INITIATED`.

---

### PUT `/api/contracts/{id}/confirm`

Confirm a contract (user or reviewer).

**Response** `200 OK`: `ContractInstance` with status `CONFIRMED`.

---

### PUT `/api/contracts/{id}/sign`

Sign a contract with a drawn signature. Contract must be `CONFIRMED`.

**Request**: `multipart/form-data`:
- `file`: PNG signature image
- `signatureType` (query param): `DRAWN`

**Response** `200 OK`: `ContractInstance` with status `SIGNED`.

---

### PUT `/api/contracts/{id}/archive`

Archive a signed contract. **Requires**: ADMIN.

**Response** `200 OK`: `ContractInstance` with status `ARCHIVED`.

---

### PUT `/api/contracts/{id}/terminate`

Terminate a contract. **Requires**: ADMIN or REVIEWER.

**Response** `200 OK`: `ContractInstance` with status `TERMINATED`.

---

### PUT `/api/contracts/{id}/void`

Void a contract. **Requires**: ADMIN.

**Response** `200 OK`: `ContractInstance` with status `VOIDED`.

---

### PUT `/api/contracts/{id}/renew`

Renew a contract with a new end date.

**Request**:
```json
{
  "newEndDate": "2026-10-15"
}
```

**Response** `200 OK`: `ContractInstance` with status `RENEWED`.

---

### GET `/api/contracts/my`

Get contracts for the authenticated user. **Response** `200 OK`: `ContractInstance[]`

### GET `/api/contracts/{id}`

Get contract detail. **Response** `200 OK`: `ContractInstance`

### GET `/api/contracts/{id}/print`

Get printable contract view. **Response** `200 OK`: `ContractInstance`

---

### POST `/api/contracts/{id}/evidence`

Upload evidence file to a contract.

**Request**: `multipart/form-data` with `file` part.

**Response** `200 OK`: `EvidenceFile` object.

---

### GET `/api/contracts/{id}/evidence`

List evidence files. **Response** `200 OK`: `EvidenceFile[]`

---

## 7. Appeals (`/api/appeals`)

### POST `/api/appeals`

Create an appeal against an order or contract.

**Request**:
```json
{
  "orderId": 1,
  "contractId": 5,
  "reason": "Items were not as described"
}
```

**Response** `200 OK`: `Appeal` with status `OPEN`.

---

### POST `/api/appeals/{id}/evidence`

Upload evidence for an appeal.

**Request**: `multipart/form-data` with `file` part.

**Response** `200 OK`: `EvidenceFile` object.

---

### PUT `/api/appeals/{id}/resolve`

Resolve an appeal. **Requires**: REVIEWER or ADMIN.

**Request**:
```json
{
  "outcome": "UPHELD",
  "reasoning": "Evidence confirms items were misrepresented."
}
```

**Response** `200 OK`: `Appeal` with status `RESOLVED`.

---

### GET `/api/appeals/{id}`

Get appeal detail with evidence and outcome.

**Response** `200 OK`:
```json
{
  "appeal": { ... },
  "evidence": [ ... ],
  "outcome": { ... }
}
```

---

### GET `/api/appeals/my`

Get appeals filed by the authenticated user. **Response** `200 OK`: `Appeal[]`

---

## 8. Reviewer (`/api/reviewer`)

All endpoints require REVIEWER or ADMIN role.

### GET `/api/reviewer/queue`

Get orders pending review. **Response** `200 OK`: `Order[]` (status=PENDING_CONFIRMATION)

---

### PUT `/api/reviewer/orders/{id}/accept`

Accept an order for review. **Response** `200 OK`: `Order` with status `ACCEPTED`.

---

### PUT `/api/reviewer/order-items/{id}/adjust-category`

Recategorize an order item.

**Request**:
```json
{
  "newCategory": "Electronics"
}
```

**Response** `200 OK`: Updated `OrderItem`.

---

### PUT `/api/reviewer/orders/{id}/approve-cancel`

Approve a cancellation (EXCEPTION state orders).

**Request**:
```json
{
  "reason": "Approved"
}
```

**Response** `200 OK`: `Order` with status `CANCELED`.

---

### POST `/api/reviewer/orders/{id}/initiate-contract`

Initiate a contract for an order.

**Request**:
```json
{
  "templateVersionId": 3,
  "userId": 5,
  "fieldValues": "{\"party_name\": \"Jane Doe\"}",
  "startDate": "2026-04-15",
  "endDate": "2026-07-15"
}
```

**Response** `200 OK`: `ContractInstance`.

---

## 9. Admin (`/api/admin`)

All endpoints require ADMIN role.

### POST `/api/admin/strategies`

Create a ranking strategy.

**Request**:
```json
{
  "versionLabel": "v2-quality-focused",
  "creditScoreWeight": 0.5,
  "positiveRateWeight": 0.3,
  "reviewQualityWeight": 0.2,
  "minCreditScoreThreshold": 30.0,
  "minPositiveRateThreshold": 0.5
}
```

**Response** `200 OK`: `RankingStrategyVersion` (inactive, must be activated).

---

### PUT `/api/admin/strategies/{id}/activate`

Activate a ranking strategy (deactivates all others).

**Response** `200 OK`: `RankingStrategyVersion`.

---

### GET `/api/admin/strategies`

List all strategies (newest first). **Response** `200 OK`: `RankingStrategyVersion[]`

### GET `/api/admin/strategies/active`

Get the active strategy. **Response** `200 OK`: `RankingStrategyVersion`

---

### GET `/api/admin/analytics/search`

Get search analytics dashboard data.

**Response** `200 OK`:
```json
{
  "totalSearches": 150,
  "totalClicks": 45,
  "clicksWithSearchContext": 38,
  "uniqueKeywords": 32,
  "topTerms": [
    { "searchTerm": "laptop", "searchCount": 12, ... }
  ],
  "topClickedItems": [
    { "itemId": 1, "itemName": "Dell XPS 13", "clickCount": 8 }
  ],
  "recentSearches": [ ... ],
  "searchTrend": [ ... ]
}
```

---

### GET `/api/admin/access-logs`

Get admin access audit logs. **Response** `200 OK`: `AdminAccessLog[]`

---

### POST `/api/admin/users/{id}/reveal`

Reveal PII for a user (logged in admin access log).

**Parameters**: `reason` (required query param)

**Response** `200 OK`: Full user profile including masked fields.

---

## 10. Search (`/api/search`)

### GET `/api/search/autocomplete`

Get search term suggestions.

**Parameters**: `q` (required) - partial search term

**Response** `200 OK`: `string[]` (up to 10 suggestions)

---

### GET `/api/search/trending`

Get trending search terms.

**Response** `200 OK`: `SearchTrend[]` (top 10)

---

## 11. Storage (`/storage`)

### GET `/storage/**`

Serve a stored file by relative path. Requires authentication.

**Example**: `GET /storage/reviews/a1b2c3d4.png`

**Response** `200 OK`: File bytes with appropriate `Content-Type` and `Cache-Control: private, max-age=3600`.

**Access control by subdirectory**:
- `signatures/*` - Contract owner only
- `evidence/*` - Uploader or contract/appeal owner
- `reviews/*` - Review author, order owner, or assigned reviewer
- Admin can access all files

**Integrity**: SHA-256 checksum verified on retrieval for all file types.

**Errors**:
- `302` redirect to `/login` - Not authenticated
- `403` - Access denied or integrity check failed
- `404` - File not found

---

## 12. Page Routes

Server-rendered Thymeleaf pages. All require authentication unless noted.

### Public Pages

| Method | Path | Template | Description |
|--------|------|----------|-------------|
| GET | `/login` | `auth/login` | Login page |
| GET | `/auth/change-password` | `auth/change-password` | Password reset page |

### User Pages

| Method | Path | Template | Description |
|--------|------|----------|-------------|
| GET | `/` | redirect to `/user/dashboard` | Root redirect |
| GET | `/user/dashboard` | `user/dashboard` | User dashboard with counts |
| GET | `/user/search` | `user/search` | Catalog search with filters |
| GET | `/user/orders` | `user/orders` | Order list |
| GET | `/user/orders/create` | `user/create-order` | Create order form |
| GET | `/user/orders/{id}` | `user/order-detail` | Order detail with logs |
| GET | `/user/reviews` | `user/reviews` | Review list |
| GET | `/user/reviews/create` | `user/create-review` | Create review form |
| GET | `/user/contracts` | `contract/list` | Contract list (filterable by status) |
| GET | `/user/contracts/{id}` | `contract/detail` | Contract detail with evidence |
| GET | `/user/contracts/{id}/sign` | `contract/sign` | Signature pad (CONFIRMED only) |
| GET | `/user/contracts/{id}/print` | `contract/print` | Printable contract view |
| GET | `/user/appeals` | `user/appeals` | Appeal list |
| GET | `/user/appeals/{id}` | `user/appeal-detail` | Appeal detail |
| GET | `/user/appeals/create` | `user/create-appeal` | Create appeal form |

### Reviewer Pages (REVIEWER or ADMIN)

| Method | Path | Template | Description |
|--------|------|----------|-------------|
| GET | `/reviewer/dashboard` | `reviewer/dashboard` | Queue count, accepted/completed stats |
| GET | `/reviewer/queue` | `reviewer/queue` | Pending orders queue |
| GET | `/reviewer/orders/{id}` | `reviewer/order-detail` | Order detail with contract initiation |

### Admin Pages (ADMIN)

| Method | Path | Template | Description |
|--------|------|----------|-------------|
| GET | `/admin/dashboard` | `admin/dashboard` | System stats (users, orders, searches) |
| GET | `/admin/strategies` | `admin/strategies` | Ranking strategy management |
| GET | `/admin/analytics` | `admin/analytics` | Search analytics dashboard |
| GET | `/admin/templates` | `admin/templates` | Contract template management |

### Contract Redirects (legacy paths)

| Method | Path | Redirects To |
|--------|------|-------------|
| GET | `/contracts` | `/user/contracts` |
| GET | `/contracts/{id}` | `/user/contracts/{id}` |
| GET | `/contracts/{id}/sign` | `/user/contracts/{id}/sign` |
| GET | `/contracts/{id}/print` | `/user/contracts/{id}/print` |
