window.onload = function() {
  window.ui = SwaggerUIBundle({
    url: "./openapi.yaml",
    defaultModelsExpandDepth: 0,
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: "StandaloneLayout"
  });
};
