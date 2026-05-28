async function testGemini() {
    const key = 'AIzaSyD04UUlvUYXN25oQKkn1VCZE5fprBOCNaI';
    const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?alt=sse&key=${key}`;
    const contents = [{ role: 'user', parts: [{ text: 'Hola' }] }];

    const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ contents }),
    });

    console.log('Status:', res.status);
    if (!res.ok) {
        console.error(await res.text());
    } else {
        console.log('Success!');
    }
}

testGemini();
