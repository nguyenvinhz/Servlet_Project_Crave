# Delivery fees and cart customization

The current `food` table belongs to `category`, with no restaurant/branch foreign key or store address. `delivery_address` belongs to customers; `employee.address` is an employee address. The current implementation uses one shared store origin in application configuration. This is an implementation choice, not an explicit requirement found in the original project documentation.

The original `class-diagram.drawio` and its mapping in `docs/architecture/README.md` model dishes/categories, customers, employees, carts, orders, promotions, and payments. They do not model restaurants, store ownership, or seller accounts. The available documentation does not explicitly require all dishes to have the same pickup address, and it also does not specify a marketplace for independent sellers.

Multiple stores are feasible as an extension: introduce a `store` entity/table with address and coordinates, then relate `food.store_id` to it. A shared store record avoids copying an address onto every dish. A limited first version could allow one store per cart/order. Supporting mixed-store carts requires deciding how to split orders and calculate shipping, vouchers, payment/refunds, pickup, and store-specific staff permissions. An independent seller marketplace additionally needs store-owner accounts and authorization. These extensions are not implemented by the fee/UI revision.

The demo address is **99 Đường Số 9, Dĩ An, Hồ Chí Minh 825030, Việt Nam**. The demo coordinates **10.8925871, 106.7637161** identify Đường số 9 in Nhị Đồng 2, from an OpenStreetMap street-level search. They are an approximate demonstration pin, not a verified entrance for house number 99. Override `CRAVE_STORE_ADDRESS`, `CRAVE_STORE_LATITUDE`, and `CRAVE_STORE_LONGITUDE` in the Tomcat environment/JVM properties with a precise entrance pin before real delivery operations. The deploy script carries those optional settings into the application container.

| Geographic distance | Fee |
| --- | ---: |
| 0 ≤ distance < 1 km | Free |
| 1 ≤ distance < 3 km | 4,000 VND |
| 3 ≤ distance < 5 km | 7,000 VND |
| 5 ≤ distance < 7 km | 7,000 + (distance − 5) × 2,500 VND |
| 7 ≤ distance ≤ 10 km | 12,000 + (distance − 7) × 3,000 VND |
| distance > 10 km | Delivery rejected |
| Pickup | 0 VND |

Food subtotal strictly greater than 150,000 VND qualifies for free shipping before voucher discounts, provided the destination is within the delivery area. Exactly 150,000 VND keeps the normal distance fee. CheckoutServlet reads the authenticated customer's verified cart subtotal; order creation recalculates from the locked persisted cart. Large orders do not bypass the delivery-radius limit.

`DeliveryFeeService` uses Haversine distance. It compares the unrounded distance with the band/radius limits. Beyond 5 km, the fare accumulates from 7,000 VND: add 2,500 VND per km between 5 and 7 km, then 3,000 VND per km beyond 7 km. Use the unrounded distance and round only the final fee to the nearest VND (HALF_UP). For example, 6 km costs 9,500 VND and 9 km costs 18,000 VND. Exactly 5 km costs 7,000 VND, exactly 7 km costs 12,000 VND, and exactly 10 km costs 21,000 VND. The internal quote/order distance snapshot is separately rounded to 0.001 km. This is a geographic radius, not a motorcycle driving route. Motorcycle routing is feasible with a suitable provider/API credential or a hosted routing engine; it is not configured in this release. A car route must not be presented as a motorcycle route.

The table and formula above are developer documentation. Customer JSPs show the store location, final delivery fee and an eligibility message. Fee calculation runs in `DeliveryFeeService`; the browser receives rendered HTML rather than a delivery JSON API.

`GET /checkout` renders the current cart, saved addresses, store coordinates and receiver profile using request/session attributes. The customer selects a saved address or types one and submits “Tìm địa chỉ”. `POST /checkout` with `action=search` calls the geocoder on the server and forwards candidate buttons to the JSP. Choosing a candidate updates the address and coordinates locally. Manual Leaflet clicks, dragging and device geolocation also update the coordinate fields; an empty address receives coordinate fallback text that follows later pin moves. Device geolocation requests a fresh high-accuracy fix. “Lấy địa chỉ tại ghim” submits `action=reverse` and renders a nearby description or coordinate fallback without changing the selected point.

“Tính lại tổng tiền” submits `action=quote`; the server reloads the authenticated customer's subtotal and promotion, calculates shipping, and renders the updated summary. Editing an address clears the selected coordinate fields. Pin changes request a new fee calculation through the same form. “Đặt hàng” submits `action=placeOrder`, where the server checks the coordinates and cart again before creating the order. Pickup ignores delivery address/coordinate fields and has no shipping charge. JavaScript handles local map/input behavior; business requests use ordinary HTML forms and full-page navigation.
Address search uses the [Photon API](https://github.com/komoot/photon/blob/master/docs/api-v1.md) with Vietnamese results and a focus around the configured store. It handles roads, numbered addresses, place names, omitted commas, diacritics/case, house suffixes/slashes, common administrative abbreviations and Hồ Chí Minh aliases. Exact house numbers and requested administrative context are checked; missing ward/district metadata marks a suggestion as approximate. A second bounded request for street results can supply an explicit road suggestion when the house is absent; it is never auto-quoted as an exact house. Distinct candidates remain available for customer selection. There are no hardcoded destination examples. Public map data can lack buildings or use changed administrative names, so suggestions require visual checking.

`CoordinateParser` accepts decimal latitude/longitude separated by comma, semicolon, or whitespace; optional parentheses, labels or `geo:`; decimal commas when separated by a semicolon; and an unambiguous longitude-first pair when its first number exceeds the latitude range. It also recognizes the parenthesized coordinates appended by reverse lookup. Invalid numeric pairs are rejected before requesting the provider. Values retain full precision for quotes/orders; labels use six decimal places. Coordinate inputs bypass external search.

Reverse lookup uses Photon's `/reverse` within 50 m and rejects descriptions from results more than 75 m away. Returned coordinates always echo the requested point, even when reusing a cached description. Search and reverse results share a bounded cache of 128 entries for six hours. Network calls have timeouts and do not hold the cache lock, so a slow name search does not serialize other users' queries. The [public demo server](https://github.com/komoot/photon#demo-server) permits reasonable volumes and has no availability guarantee; `CRAVE_GEOCODER_URL` can point to a compatible hosted/private instance.
Leaflet 1.9.4 and its license are vendored locally. Tiles use the [OpenStreetMap tile service](https://operations.osmfoundation.org/policies/tiles/) with visible attribution, normal browser caching, and no prefetch/download feature. Tile availability is best-effort; manual pins remain the fallback when external address lookup is unavailable.

## Servlet routes and form fields

Protected routes use the authenticated customer session and the existing origin checks. GET requests load beans into request attributes and forward to JSP. Successful cart writes redirect to GET so refreshing a page does not repeat the write.

| Route | Method / fields | Result |
| --- | --- | --- |
| `/cart` | GET; optional `edit=cartItemId` | Cart totals, voucher feedback and optional item editor rendered by JSP. |
| `/cart/items` | POST `action=add`, `foodId`, `quantity`, `note`, option fields | Add the selected dish, then redirect to cart or a validated local menu return path. |
| `/cart/items` | POST `action=update`, `cartItemId`, `quantity` | Change quantity while preserving omitted note/options; quantity 0 removes the item. |
| `/cart/items` | POST `action=update`, `customization=true`, `cartItemId`, `quantity`, `note`, option fields | Save all customization fields; no selected option fields clears the options. |
| `/cart/items` | POST `action=remove`, `cartItemId` | Remove one owned cart line. |
| `/cart/clear` | POST | Clear the cart and selected promotion. |
| `/cart/promotion` | POST `code`; or `action=remove` | Validate/store `appliedPromotionCode` in the session or remove it. |
| `/promotions` | GET | Render the active promotion list. “Dùng ngay” submits the promotion form. |
| `/checkout` | POST `action=search`, `deliveryAddress` | Render address candidates from the server geocoder. |
| `/checkout` | POST `action=reverse`, `deliveryLatitude`, `deliveryLongitude` | Render the description for the selected pin. |
| `/checkout` | POST `action=quote`, fulfillment/location fields | Render server-calculated shipping, discount and total. |
| `/checkout` | POST `action=placeOrder`, receiver, fulfillment, payment, note and delivery fields | Create the order and redirect to `/orders/{id}`. |

Multiple-choice options use repeated `optionIds` parameters. Size, sugar and ice radio groups use `option_SIZE`, `option_SUGAR_LEVEL` and `option_ICE_LEVEL`. `CartServlet` reads them with `getParameterValues`; it does not deserialize JSON. The service/DAO validates ownership, food availability, option-to-food membership, active status, duplicates and singleton groups. Quantity, note and option changes save in one transaction, locking the cart before the item to coordinate with checkout. Unchanged option rows remain intact. Edited items retain their IDs and remain separate from other cart lines even if selections become identical.

The dish name or “Chỉnh sửa món” links to `GET /cart?edit=...`. Cancel returns to the cart without a write. Cart rendering revalidates the session promotion after each change; checkout validates it again. The displayed cart total excludes shipping until checkout supplies a delivery location. Relative cart image paths are resolved under the application context, including the `/cart/` URL.
## Database and release

Apply `database/migrations/2026-10-15-delivery-location.sql` **before** starting the new WAR. It adds nullable `delivery_latitude`, `delivery_longitude`, and `delivery_distance_km` to `customer_order`, preserves historical orders, and supports repeated execution. New databases use the updated `schema.sql`. The existing EC2 deploy script includes this migration. Historical orders retain their existing delivery fees; new orders use the distance bands.

## Verification

- Backend coverage includes `DeliveryFeeServiceTest`, `CartCustomizationServiceTest`, `CartItemCustomizationDAOTest`, `CartServletTest` and `AuthenticatedOrderPaymentServletTest`. Servlet tests check form parsing, session identity, forwards/redirects, voucher revalidation and pickup ignoring retained malformed coordinates.
- `ServletStructureTest` checks the Servlet/JSP boundary, unique routes, absence of legacy mapper/API packages and browser JSON transport, and continued JPA/Hikari configuration.
- `node tests/frontend/cart-checkout-browser-smoke.cjs` runs against the real packaged application in Chrome/Edge. Set `CRAVE_SMOKE_BASE_URL`, `CRAVE_SMOKE_CUSTOMER_EMAIL` and `CRAVE_SMOKE_CUSTOMER_PASSWORD` for a disposable customer with an empty cart; optionally set `CHROME_PATH`. It checks actual menu/cart forms, customization, UTF-8 labels, accessible Save controls, quantity/note persistence, mobile layout and pickup quotation. It also verifies that browser requests do not use `/api/`, Fetch or XHR.
- `node tests/runtime/http-smoke.cjs` exercises the live JSP/form workflows against a disposable seeded database and local SMTP fixture. It writes browser fixture credentials to ignored `backend/target/runtime-customer.env`.
- Real MySQL coverage remains in `CartPromotionIntegrationTest` and `CartCheckoutIntegrationTest`; it requires the migration and working database credentials. Local runtime testing uses an isolated `crave_local_test` database. These tests also cover reading detached cart items for option validation, so food/cart relations must be loaded before closing the DAO EntityManager.
