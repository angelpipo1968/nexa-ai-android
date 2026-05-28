/**
 * NEXA CORE — Servicio de Finanzas
 * Proporciona precios de bolsa (Alpha Vantage) y criptomonedas (CoinGecko).
 */

export async function getStockPrice(symbol: string): Promise<string> {
    const apiKey = process.env.ALPHA_VANTAGE_API_KEY;
    if (!apiKey) return "Falta ALPHA_VANTAGE_API_KEY.";

    try {
        const url = `https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${symbol}&apikey=${apiKey}`;
        const res = await fetch(url);
        const data = await res.json();
        
        const quote = data['Global Quote'];
        if (!quote || !quote['05. price']) return `No pude encontrar datos para el símbolo "${symbol}".`;

        return `BOLSA (${symbol.toUpperCase()}):
Precio: $${parseFloat(quote['05. price']).toFixed(2)}
Cambio: ${quote['10. change percent']}
Volumen: ${quote['06. volume']}`;
    } catch (error: any) {
        return `Error al consultar bolsa: ${error.message}`;
    }
}

export async function getCryptoPrice(coin: string): Promise<string> {
    try {
        // CoinGecko no requiere key para uso básico
        const url = `https://api.coingecko.com/api/v3/simple/price?ids=${coin.toLowerCase()}&vs_currencies=usd&include_24hr_change=true`;
        const res = await fetch(url);
        const data = await res.json();
        
        if (!data[coin.toLowerCase()]) return `No pude encontrar datos para la cripto "${coin}".`;

        const info = data[coin.toLowerCase()];
        return `CRIPTO (${coin.toUpperCase()}):
Precio: $${info.usd.toLocaleString()}
Cambio 24h: ${info.usd_24h_change?.toFixed(2)}%`;
    } catch (error: any) {
        return `Error al consultar cripto: ${error.message}`;
    }
}
