import { NextRequest, NextResponse } from 'next/server';
import { getWeather } from '@/lib/nexa-core/weather';

export const dynamic = 'force-dynamic';

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

export async function OPTIONS() {
    return new NextResponse(null, { headers: corsHeaders });
}

export async function GET(req: NextRequest) {
    const { searchParams } = new URL(req.url);
    const city = searchParams.get('city');
    const accuKey = process.env.ACCUWEATHER_API_KEY;

    if (!city) {
        return NextResponse.json({ error: 'Missing city parameter' }, { status: 400, headers: corsHeaders });
    }

    try {
        if (accuKey) {
            // 1. Get Location Key
            const locRes = await fetch(`http://dataservice.accuweather.com/locations/v1/cities/search?apikey=${accuKey}&q=${encodeURIComponent(city)}&language=es-es`);
            const locData = await locRes.json();
            
            if (locRes.ok && locData.length > 0) {
                const locationKey = locData[0].Key;
                const cityName = locData[0].LocalizedName;

                // 2. Get Current Conditions
                const weatherRes = await fetch(`http://dataservice.accuweather.com/currentconditions/v1/${locationKey}?apikey=${accuKey}&language=es-es&details=true`);
                const weatherData = await weatherRes.json();

                if (weatherRes.ok && weatherData.length > 0) {
                    const current = weatherData[0];
                    return NextResponse.json({
                        source: 'AccuWeather',
                        city: cityName,
                        temp: current.Temperature.Metric.Value,
                        condition: current.WeatherText,
                        humidity: current.RelativeHumidity,
                        wind: current.Wind.Speed.Metric.Value,
                        uvIndex: current.UVIndex,
                        report: `CLIMA EN ${cityName.toUpperCase()} (AccuWeather):\n${current.WeatherText}, ${current.Temperature.Metric.Value}°C. Humedad: ${current.RelativeHumidity}%, Viento: ${current.Wind.Speed.Metric.Value} km/h.`
                    }, { headers: corsHeaders });
                }
            }
        }
        
        // Fallback to existing wttr.in logic
        const report = await getWeather(city);
        return NextResponse.json({ source: 'wttr.in', report }, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
