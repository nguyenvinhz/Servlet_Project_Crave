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

Food subtotal strictly greater than 150,000 VND qualifies for free shipping before voucher discounts, provided the destination is within the delivery area. Exactly 150,000 VND keeps the normal distance fee. The quote API reads the authenticated customer's verified cart subtotal; order creation recalculates from the locked persisted cart. Large orders do not bypass the delivery-radius limit.

`DeliveryFeeService` uses Haversine distance. It compares the unrounded distance with the band/radius limits. Beyond 5 km, the fare accumulates from 7,000 VND: add 2,500 VND per km between 5 and 7 km, then 3,000 VND per km beyond 7 km. Use the unrounded distance and round only the final fee to the nearest VND (HALF_UP). For example, 6 km costs 9,500 VND and 9 km costs 18,000 VND. Exactly 5 km costs 7,000 VND, exactly 7 km costs 12,000 VND, and exactly 10 km costs 21,000 VND. The internal quote/order distance snapshot is separately rounded to 0.001 km. This is a geographic radius, not a motorcycle driving route. Motorcycle routing is feasible with a suitable provider/API credential or a hosted routing engine; it is not configured in this release. A car route must not be presented as a motorcycle route.

The table and formula above are developer documentation. Customer pages do not show fee bands, distance calculations, distance thresholds, or a service-radius circle. Customer delivery APIs return store location/configuration and the final fee/eligibility message, without the pricing table, distance method, maximum radius, or computed quote distance.

Checkout searches the default/selected address automatically. An exact, unique house match receives an automatic quote. Road/locality/place-name suggestions need pin confirmation, and ambiguous matches appear as a list of named choices instead of being discarded or silently selecting the first. Editing the text invalidates the old pin and quote; pending/debounced results cannot replace a newer choice. Manual map clicks, dragging, and device geolocation work with an empty address field. Each manual selection immediately fills decimal latitude/longitude in the address field, then optionally retrieves a nearby address in the background. That description includes the selected coordinates and never moves the pin. Missing, distant, or failed reverse results keep the coordinate text and do not block quoting or ordering. Reverse responses cannot overwrite later pin moves, typed text, pickup selection, or an order being submitted. Moving the pin invalidates the fee; press “Xác nhận điểm nhận” to quote the new point. The same coordinates are sent to quote and order APIs and persisted on the order. Browser geolocation uses a fresh high-accuracy request where supported; manual map selection also works on HTTP origins.

Address search uses the [Photon API](https://github.com/komoot/photon/blob/master/docs/api-v1.md) with Vietnamese results and a focus around the configured store. It handles roads, numbered addresses, place names, omitted commas, diacritics/case, house suffixes/slashes, common administrative abbreviations and Hồ Chí Minh aliases. Exact house numbers and requested administrative context are checked; missing ward/district metadata marks a suggestion as approximate. A second bounded request for street results can supply an explicit road suggestion when the house is absent; it is never auto-quoted as an exact house. Distinct candidates remain available for customer selection. There are no hardcoded destination examples. Public map data can lack buildings or use changed administrative names, so suggestions require visual checking.

`CoordinateParser` accepts decimal latitude/longitude separated by comma, semicolon, or whitespace; optional parentheses, labels or `geo:`; decimal commas when separated by a semicolon; and an unambiguous longitude-first pair when its first number exceeds the latitude range. It also recognizes the parenthesized coordinates appended by reverse lookup. Invalid numeric pairs are rejected before requesting the provider. Values retain full precision for quotes/orders; labels use six decimal places. Coordinate inputs bypass external search.

Reverse lookup uses Photon's `/reverse` within 50 m and rejects descriptions from results more than 75 m away. Returned coordinates always echo the requested point, even when reusing a cached description. Search and reverse results share a bounded cache of 128 entries for six hours. Network calls have timeouts and do not hold the cache lock, so a slow name search does not serialize other users' queries. The [public demo server](https://github.com/komoot/photon#demo-server) permits reasonable volumes and has no availability guarantee; `CRAVE_GEOCODER_URL` can point to a compatible hosted/private instance.
Leaflet 1.9.4 and its license are vendored locally. Tiles use the [OpenStreetMap tile service](https://operations.osmfoundation.org/policies/tiles/) with visible attribution, normal browser caching, and no prefetch/download feature. Tile availability is best-effort; manual pins remain the fallback when external address lookup is unavailable.

## APIs

All endpoints use the authenticated customer session, existing origin checks, and the common `{success,data,error}` envelope.

- `GET /api/delivery/policy`: `{store,configured}` for initializing the map.
- `POST /api/delivery/search`: `{address}`; returns a list of `{latitude,longitude,label,approximate}` candidates, including direct coordinate input.
- `POST /api/delivery/reverse`: `{latitude,longitude}`; returns a nearby description or coordinate fallback without moving the selected point.
- `POST /api/delivery/resolve`: `{address}`; returns `{latitude,longitude,label,approximate}`. Does not return pricing rules.
- `POST /api/delivery/quote`: body `{latitude,longitude}`; returns `{deliveryFee,deliverable,message}`. An outside-radius quote has `deliverable:false`, `deliveryFee:null`, and a general unsupported-area message.
- `POST /api/orders`: delivery requests require `deliveryLatitude` and `deliveryLongitude`. The server recalculates the fee and rejects invalid/outside-radius coordinates, regardless of a previous quote. Pickup clears the address and coordinates and costs 0 VND. The customer-selected pin is the authoritative drop-off point.
- `POST /api/cart/items/update`: `{cartItemId,quantity,note,optionIds}`. Omitted/null `optionIds` preserves current options; `[]` clears them. The server validates ownership, availability, option-to-food membership, active status, duplicates, and singleton groups. Quantity, note, and option changes save in one transaction, locking the cart before the item to coordinate with checkout. Unchanged option rows remain intact. Edited items retain their IDs and remain separate from other cart lines even if selections become identical.

Clicking the dish name, image/card content, or “Chỉnh sửa món” opens the cart editor. Cancel writes nothing. Successful edits reload server totals and revalidate any applied voucher. Cart shows the food total after discounts; shipping is quoted only at checkout.

## Database and release

Apply `database/migrations/2026-10-15-delivery-location.sql` **before** starting the new WAR. It adds nullable `delivery_latitude`, `delivery_longitude`, and `delivery_distance_km` to `customer_order`, preserves historical orders, and supports repeated execution. New databases use the updated `schema.sql`. The existing EC2 deploy script includes this migration. Historical orders retain their existing delivery fees; new orders use the distance bands.

## Verification

- Backend unit/API tests: `DeliveryFeeServiceTest`, `DeliveryApiServletTest`, `CartCustomizationServiceTest`, `CartItemCustomizationRepositoryTest`, plus existing cart/order/authorization tests.
- Browser smoke: `node tests/frontend/cart-checkout-browser-smoke.cjs` runs the actual JSP markup/scripts in Chrome with controlled API responses. It checks option editing, cancellation, double submission, failures/retries, voucher recalculation, quote races, address changes, outside-radius blocking, and pickup.
- Real MySQL coverage is in `CartPromotionIntegrationTest` and `CartCheckoutIntegrationTest`; it requires the migration and working local DB credentials. Local runtime testing uses an isolated `crave_local_test` database. These tests also cover reading detached cart items for option validation, so food/cart relations must be loaded before closing the repository EntityManager.
