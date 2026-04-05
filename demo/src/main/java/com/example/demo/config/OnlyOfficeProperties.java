package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "onlyoffice")
public class OnlyOfficeProperties {

    private String documentServerUrl = "";
    private String publicBaseUrl = "";
    private String jwtSecret = "";
    private String defaultMode = "edit";
    private List<String> pluginConfigUrls = new ArrayList<>();
    private List<String> autostartPluginGuids = new ArrayList<>();
    private Map<String, Object> pluginOptions = new LinkedHashMap<>();
    private H5Game h5Game = new H5Game();

    public String getDocumentServerUrl() {
        return documentServerUrl;
    }

    public void setDocumentServerUrl(String documentServerUrl) {
        this.documentServerUrl = documentServerUrl;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public List<String> getPluginConfigUrls() {
        return pluginConfigUrls;
    }

    public void setPluginConfigUrls(List<String> pluginConfigUrls) {
        this.pluginConfigUrls = pluginConfigUrls == null ? new ArrayList<>() : new ArrayList<>(pluginConfigUrls);
    }

    public List<String> getAutostartPluginGuids() {
        return autostartPluginGuids;
    }

    public void setAutostartPluginGuids(List<String> autostartPluginGuids) {
        this.autostartPluginGuids = autostartPluginGuids == null ? new ArrayList<>() : new ArrayList<>(autostartPluginGuids);
    }

    public Map<String, Object> getPluginOptions() {
        return pluginOptions;
    }

    public void setPluginOptions(Map<String, Object> pluginOptions) {
        this.pluginOptions = pluginOptions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pluginOptions);
    }

    public H5Game getH5Game() {
        return h5Game;
    }

    public void setH5Game(H5Game h5Game) {
        this.h5Game = h5Game == null ? new H5Game() : h5Game;
    }

    public static class H5Game {
        private String pluginFolder = "h5-game";
        private String pluginGuid = "asc.{FFE1F462-1E74-4D2C-8C1F-0A0A0A0A0A01}";
        private String previewImageUrl = "";
        private Double defaultWidthMm = 120.0;
        private Double defaultHeightMm = 90.0;

        public String getPluginFolder() {
            return pluginFolder;
        }

        public void setPluginFolder(String pluginFolder) {
            this.pluginFolder = pluginFolder;
        }

        public String getPluginGuid() {
            return pluginGuid;
        }

        public void setPluginGuid(String pluginGuid) {
            this.pluginGuid = pluginGuid;
        }

        public String getPreviewImageUrl() {
            return previewImageUrl;
        }

        public void setPreviewImageUrl(String previewImageUrl) {
            this.previewImageUrl = previewImageUrl;
        }

        public Double getDefaultWidthMm() {
            return defaultWidthMm;
        }

        public void setDefaultWidthMm(Double defaultWidthMm) {
            this.defaultWidthMm = defaultWidthMm;
        }

        public Double getDefaultHeightMm() {
            return defaultHeightMm;
        }

        public void setDefaultHeightMm(Double defaultHeightMm) {
            this.defaultHeightMm = defaultHeightMm;
        }
    }
}
