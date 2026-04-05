(function () {
  function byId(id) {
    return document.getElementById(id);
  }

  function pluginInfo() {
    return window.Asc && window.Asc.plugin ? window.Asc.plugin.info || {} : {};
  }

  function parseJson(value) {
    if (!value || typeof value !== "string") {
      return null;
    }
    try {
      return JSON.parse(value);
    } catch (error) {
      return null;
    }
  }

  function isObject(value) {
    return !!value && typeof value === "object" && !Array.isArray(value);
  }

  function withGuidOption(options, guid) {
    if (!isObject(options)) {
      return null;
    }
    if (guid && isObject(options[guid])) {
      return options[guid];
    }
    if (typeof options.prepareApi === "string" && options.prepareApi.trim()) {
      return options;
    }
    return null;
  }

  function urlOptions() {
    var sources = [];
    try {
      sources.push(new URLSearchParams(window.location.search));
      if (window.location.hash && window.location.hash.length > 1) {
        sources.push(new URLSearchParams(window.location.hash.substring(1)));
      }
    } catch (error) {
      return null;
    }

    for (var i = 0; i < sources.length; i += 1) {
      var params = sources[i];
      var rawOptions = params.get("pluginOptions") || params.get("options");
      var parsedOptions = parseJson(rawOptions);
      if (isObject(parsedOptions)) {
        return parsedOptions;
      }

      var prepareApi = params.get("prepareApi");
      if (prepareApi) {
        return {
          prepareApi: prepareApi,
          pluginGuid: params.get("pluginGuid") || ""
        };
      }
    }

    return null;
  }

  function pluginOptions() {
    var info = pluginInfo();
    var guid = info.guid || "";
    var directOptions = withGuidOption(info.options, guid);
    if (directOptions) {
      return directOptions;
    }

    var parsedInitData = parseJson(info.initData);
    var initDataOptions = withGuidOption(parsedInitData, guid);
    if (initDataOptions) {
      return initDataOptions;
    }

    if (isObject(info.data)) {
      var dataOptions = withGuidOption(info.data, guid);
      if (dataOptions) {
        return dataOptions;
      }
    }

    var fallbackUrlOptions = urlOptions();
    var urlResolvedOptions = withGuidOption(fallbackUrlOptions, guid);
    if (urlResolvedOptions) {
      return urlResolvedOptions;
    }

    return {};
  }

  function text(value, fallbackValue) {
    var normalized = String(value || "").trim();
    return normalized || fallbackValue;
  }

  function optionalPositiveNumber(value) {
    var normalized = String(value || "").trim();
    if (!normalized) {
      return null;
    }
    var parsed = Number(normalized);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return null;
    }
    return parsed;
  }

  function defaultBucketName(options) {
    return text(options.defaultBucket, "ppt-files");
  }

  function defaultEntryFile(options) {
    return text(options.defaultEntryFile, "index.html");
  }

  function setStatus(message, type) {
    var el = byId("status");
    el.textContent = message || "";
    el.className = "status" + (type ? " " + type : "");
  }

  function setLoading(loading) {
    ["gameName", "bucketName", "gameDir", "entryFile", "widthMm", "heightMm"].forEach(function (id) {
      var el = byId(id);
      if (!el) {
        return;
      }
      if (id === "bucketName" && el.readOnly) {
        return;
      }
      el.disabled = loading;
    });
  }

  function syncPreview() {
    var options = pluginOptions();
    var gameName = text(byId("gameName").value, "Pending");
    var bucketName = text(byId("bucketName").value, defaultBucketName(options));
    var gameDir = text(byId("gameDir").value, "Pending");
    var entryFile = text(byId("entryFile").value, defaultEntryFile(options));
    var widthMm = optionalPositiveNumber(byId("widthMm").value);
    var heightMm = optionalPositiveNumber(byId("heightMm").value);

    byId("previewTitle").textContent = gameName;
    byId("previewBucket").textContent = bucketName;
    byId("previewDir").textContent = gameDir;
    byId("previewEntry").textContent = entryFile;
    byId("previewSize").textContent =
      widthMm && heightMm ? widthMm + " mm x " + heightMm + " mm" : "Default size";
    byId("resolvedHint").textContent =
      !text(byId("gameDir").value, "") ? "Enter the game directory to resolve the final MinIO object path." : "Resolved path: " + bucketName + "/" + gameDir + "/" + entryFile;
  }

  async function submit() {
    var options = pluginOptions();
    var prepareApi = options.prepareApi;
    var pluginGuid = options.pluginGuid || pluginInfo().guid || "";
    var payload = {
      gameName: text(byId("gameName").value, "H5 Game"),
      bucketName: text(byId("bucketName").value, defaultBucketName(options)),
      gameDir: text(byId("gameDir").value, ""),
      entryFile: text(byId("entryFile").value, defaultEntryFile(options)),
      pluginGuid: pluginGuid,
      widthMm: optionalPositiveNumber(byId("widthMm").value),
      heightMm: optionalPositiveNumber(byId("heightMm").value)
    };

    if (!prepareApi) {
      setStatus("Missing prepareApi configuration.", "error");
      return;
    }

    if (!payload.gameDir) {
      setStatus("Please enter the game directory.", "error");
      return;
    }

    setLoading(true);
    setStatus("Checking MinIO objects and preparing card data...", "loading");

    try {
      var response = await fetch(prepareApi, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });
      var result = await response.json();

      if (!response.ok || !result || result.code !== 0) {
        throw new Error(result && result.message ? result.message : "Prepare insertion failed.");
      }

      setStatus("Card data prepared. Inserting into the current slide...", "success");
      window.Asc.plugin.executeMethod("AddOleObject", [result.data.oleData], function () {
        window.Asc.plugin.executeCommand("close", "");
      });
    } catch (error) {
      setStatus(error && error.message ? error.message : "Insertion failed.", "error");
      setLoading(false);
    }
  }

  function initDefaults() {
    var options = pluginOptions();

    byId("bucketName").value = defaultBucketName(options);
    byId("bucketName").readOnly = true;
    byId("bucketName").title = "Using the backend default bucket.";
    byId("entryFile").value = defaultEntryFile(options);
    byId("widthMm").value = options.defaultWidthMm || "";
    byId("heightMm").value = options.defaultHeightMm || "";
    byId("gameName").value = "";
    byId("gameDir").value = "";

    ["gameName", "bucketName", "gameDir", "entryFile", "widthMm", "heightMm"].forEach(function (id) {
      byId(id).addEventListener("input", syncPreview);
    });

    syncPreview();
    setStatus("Ready to insert an H5 card from the default MinIO bucket.", "");
  }

  window.Asc.plugin.init = function () {
    initDefaults();
  };

  window.Asc.plugin.button = function (id) {
    if (id === 0) {
      submit();
      return;
    }
    window.Asc.plugin.executeCommand("close", "");
  };
})();
