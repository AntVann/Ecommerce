import { describe, expect, it } from 'vitest';
import { addressSchema } from './address';
describe('address validation', () => { it('normalizes country codes', () => expect(addressSchema.parse({ recipient: 'A', line1: '1 Main', city: 'Town', region: 'CA', postalCode: '90210', countryCode: 'us' }).countryCode).toBe('US')); it('rejects incomplete addresses', () => expect(addressSchema.safeParse({ recipient: '', countryCode: 'USA' }).success).toBe(false)); });
