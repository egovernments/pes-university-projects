import { useState } from "react";
import InputsPanel from "./flow/InputsPanel.jsx";
import LoadingView from "./flow/LoadingView.jsx";
import ResultsView from "./flow/ResultsView.jsx";
import { submitCompute, fetchStatus, fetchGeojson } from "./flow/api.js";
import "./flow/flow.css";

const POLL_INTERVAL_MS = 3000;
// The first run for a country can download WorldPop rasters for hours, so poll
// patiently against a wall-clock ceiling rather than a small fixed count.
const MAX_WAIT_MS = 12 * 60 * 60 * 1000; // 12 hours

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export default function App() {
  const [stage, setStage] = useState("input");
  const [error, setError] = useState(null);
  const [iso3, setIso3] = useState("TCD");
  const [message, setMessage] = useState("");
  const [percent, setPercent] = useState(null);
  const [result, setResult] = useState(null);
  const [geojson, setGeojson] = useState(null);
  const [householdSize, setHouseholdSize] = useState(undefined);

  const onCompute = async (params) => {
    setError(null);
    setIso3(params.iso3);
    setHouseholdSize(params.householdSize);
    setMessage("preparing");
    setPercent(null);
    setStage("loading");
    try {
      const { statusUrl } = await submitCompute(params);
      const computed = await pollUntilDone(statusUrl, (status) => {
        if (status.message) setMessage(status.message);
        setPercent(typeof status.percent === "number" ? status.percent : null);
      });
      const geo = await fetchGeojson(computed.geojsonUrl);
      setResult(computed);
      setGeojson(geo);
      setStage("results");
    } catch (failure) {
      setError(failure.message);
      setStage("input");
    }
  };

  const reset = () => {
    setStage("input");
    setResult(null);
    setGeojson(null);
  };

  if (stage === "results") {
    return (
      <div className="flow-app">
        <ResultsView
          result={result}
          geojson={geojson}
          initialHouseholdSize={householdSize}
          onReset={reset}
        />
      </div>
    );
  }

  return (
    <div className="app">
      <main className="content compute-main">
        {stage === "loading"
          ? <LoadingView iso3={iso3} message={message} percent={percent} />
          : <InputsPanel onCompute={onCompute} error={error} />}
      </main>
    </div>
  );
}

async function pollUntilDone(statusUrl, onProgress) {
  const deadline = Date.now() + MAX_WAIT_MS;
  while (Date.now() < deadline) {
    await sleep(POLL_INTERVAL_MS);
    const status = await fetchStatus(statusUrl);
    onProgress(status);
    if (status.status === "DONE") return status.result;
    if (status.status === "FAILED") throw new Error(status.error || "The engine failed.");
  }
  throw new Error("The compute has been running for over 12 hours. Check the API logs.");
}
