# Search Service

Owns the non-authoritative OpenSearch product projection and operational rebuild state. It listens
on port 8085 and owns only its inbox/checkpoint database on local port 5437.

Catalog events index or remove products. Seller suspension/rejection events remove all seller
documents. A rebuild creates a versioned index from the protected Catalog export and atomically
switches `marketflow-products-current` after indexing completes.
