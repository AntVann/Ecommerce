import { z } from 'zod';
export const addressSchema = z.object({ recipient: z.string().min(1), line1: z.string().min(1), line2: z.string().optional(), city: z.string().min(1), region: z.string().min(1), postalCode: z.string().min(2), countryCode: z.string().length(2).transform((v) => v.toUpperCase()) });
