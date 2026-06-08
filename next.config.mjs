const isCapacitorStaticExport = process.env.CAPACITOR_STATIC_EXPORT === '1';

/** @type {import('next').NextConfig} */
const nextConfig = {
    reactStrictMode: true,
    images: {
        unoptimized: true,
    },
    compress: true,
    ...(isCapacitorStaticExport ? { output: 'export' } : {}),
};

export default nextConfig;
