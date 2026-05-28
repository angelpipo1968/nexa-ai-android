async function listModels() {
    const key = 'AIzaSyCKuv1Yxy_XLSWLcemfcw_L3ghXb2Ri6HA';
    const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${key}`;
    const res = await fetch(url);
    const data = await res.json();
    console.log(JSON.stringify(data, null, 2));
}

listModels();
