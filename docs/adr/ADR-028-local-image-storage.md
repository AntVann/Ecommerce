# ADR-028: Local image storage for the $0 UI profile

## Status

Accepted

## Decision

Catalog accepts validated JPEG and PNG multipart uploads and stores the bytes in a configurable
local directory. The database stores only generated object keys and image metadata. Public image
reads are allowed only when the owning product is published and its seller is approved.

## Rationale

The portfolio project must remain free to run. A local adapter provides a real upload path for the
UI without introducing cloud credentials, paid object storage, or provider-specific SDKs. The
adapter is isolated so a future object-storage implementation can replace it without changing the
catalog aggregate or API metadata model.

## Operational notes

Set `MARKETFLOW_IMAGE_STORAGE_DIR` to a persistent local volume when running containers. Files are
not a backup substitute; the local demo profile documents this limitation.
