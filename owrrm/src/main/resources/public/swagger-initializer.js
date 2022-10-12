window.onload = function() {
  window.ui = SwaggerUIBundle({
    url: "./openapi.yaml",
    defaultModelsExpandDepth: 0,
    dom_id: '#swagger-ui',
    deepLinking: true,
    syntaxHighlight: {
      /* https://github.com/swagger-api/swagger-ui/issues/3832 */
      activated: false,
      theme: "agate"
    },
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
