/**
 * NEXA CORE — Servicio de Películas (TMDB)
 * Proporciona información sobre cine, series y actores.
 */

export interface MovieResult {
    id: number;
    title: string;
    overview: string;
    release_date: string;
    vote_average: number;
    poster_path: string | null;
}

export async function searchMovies(query: string): Promise<string> {
    const apiKey = process.env.TMDB_API_KEY;
    
    if (!apiKey) {
        return "Configuración incompleta: Falta TMDB_API_KEY.";
    }

    try {
        const url = `https://api.themoviedb.org/3/search/movie?api_key=${apiKey}&query=${encodeURIComponent(query)}&language=es-ES`;
        const res = await fetch(url);
        const data = await res.json();

        if (!res.ok || !data.results || data.results.length === 0) {
            return `No encontré películas relacionadas con "${query}".`;
        }

        const movie: MovieResult = data.results[0];
        const report = `DETALLES DE PELÍCULA:
Título: ${movie.title}
Fecha de estreno: ${movie.release_date}
Puntuación: ${movie.vote_average}/10
Resumen: ${movie.overview}
Poster: https://image.tmdb.org/t/p/w500${movie.poster_path}`;

        return report;
    } catch (error: any) {
        return `Error al consultar TMDB: ${error.message}`;
    }
}

export async function getTrendingMovies(): Promise<string> {
    const apiKey = process.env.TMDB_API_KEY;
    if (!apiKey) return "Falta TMDB_API_KEY.";

    try {
        const url = `https://api.themoviedb.org/3/trending/movie/day?api_key=${apiKey}&language=es-ES`;
        const res = await fetch(url);
        const data = await res.json();
        
        const titles = data.results.slice(0, 5).map((m: any) => `- ${m.title} (${m.release_date})`).join('\n');
        return `PELÍCULAS TENDENCIA HOY:\n${titles}`;
    } catch (error: any) {
        return `Error al obtener tendencias: ${error.message}`;
    }
}
