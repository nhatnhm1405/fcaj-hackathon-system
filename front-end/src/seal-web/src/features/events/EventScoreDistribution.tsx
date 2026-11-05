import { useEffect, useMemo, useState } from "react";
import {
  Bar, BarChart, CartesianGrid, Cell, ReferenceLine,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { C, PixelCard } from "@/shared/components/PixelComponents";
import {
  adminApi, apiErrorMessage,
  type AdminScoreDistribution,
  type ScoreDistributionMetric,
} from "@/shared/apiClient";

interface EventScoreDistributionProps {
  eventId: number;
}

const FILTER_STYLE: React.CSSProperties = {
  minWidth: 180,
  padding: "9px 12px",
  background: C.surface2,
  border: `1px solid ${C.border}`,
  color: C.text,
  fontFamily: "'JetBrains Mono', monospace",
  fontSize: 12,
  outline: "none",
  borderRadius: 0,
};

const cellStyle: React.CSSProperties = {
  padding: '10px 14px',
  color: C.text,
  fontFamily: "'JetBrains Mono', monospace",
  fontSize: 11,
};

const METRICS: Array<{ value: ScoreDistributionMetric; label: string }> = [
  { value: 'JUDGE_EVALUATION', label: 'Judge evaluations' },
  { value: 'SUBMISSION_RESULT', label: 'Submission results' },
  { value: 'CRITERIA_SCORE', label: 'Criteria scores' },
];

const STAT_ACCENTS = [
  { main: '#4ade80', soft: 'rgba(74,222,128,0.09)', border: 'rgba(74,222,128,0.38)' },
  { main: '#60a5fa', soft: 'rgba(96,165,250,0.09)', border: 'rgba(96,165,250,0.38)' },
  { main: '#a78bfa', soft: 'rgba(167,139,250,0.09)', border: 'rgba(167,139,250,0.38)' },
  { main: '#22d3ee', soft: 'rgba(34,211,238,0.09)', border: 'rgba(34,211,238,0.38)' },
  { main: '#facc15', soft: 'rgba(250,204,21,0.09)', border: 'rgba(250,204,21,0.38)' },
  { main: '#fb923c', soft: 'rgba(251,146,60,0.09)', border: 'rgba(251,146,60,0.38)' },
  { main: '#f472b6', soft: 'rgba(244,114,182,0.09)', border: 'rgba(244,114,182,0.38)' },
  { main: '#fb7185', soft: 'rgba(251,113,133,0.09)', border: 'rgba(251,113,133,0.38)' },
  { main: '#a3e635', soft: 'rgba(163,230,53,0.09)', border: 'rgba(163,230,53,0.38)' },
] as const;

function fixed(value: number | null | undefined, digits = 2) {
  return value === undefined || value === null ? '—' : value.toFixed(digits);
}

export function EventScoreDistribution({ eventId }: EventScoreDistributionProps) {
  const [data, setData] = useState<AdminScoreDistribution | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [roundId, setRoundId] = useState<number>();
  const [trackId, setTrackId] = useState<number>();
  const [criteriaId, setCriteriaId] = useState<number>();
  const [metric, setMetric] = useState<ScoreDistributionMetric>('JUDGE_EVALUATION');
  const [selectedBinIndex, setSelectedBinIndex] = useState<number>();

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    adminApi.getEventScoreDistribution(eventId, {
      roundId, trackId, metric,
      criteriaId: metric === 'CRITERIA_SCORE' ? criteriaId : undefined,
    })
      .then(response => { if (!cancelled) setData(response.data); })
      .catch(fetchError => {
        if (!cancelled) {
          setData(null);
          setError(apiErrorMessage(fetchError, 'Failed to load score distribution.'));
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [eventId, roundId, trackId, metric, criteriaId]);

  const selectedBin = selectedBinIndex === undefined ? undefined : data?.bins[selectedBinIndex];
  const selectedObservations = useMemo(() => {
    if (!selectedBin || !data) return [];
    return data.observations.filter(observation =>
      observation.score >= selectedBin.lowerBound
      && (selectedBin.upperBound === 100
        ? observation.score <= selectedBin.upperBound
        : observation.score < selectedBin.upperBound));
  }, [data, selectedBin]);

  function changeRound(value: string) {
    setRoundId(Number(value));
    setCriteriaId(undefined);
    setSelectedBinIndex(undefined);
  }

  function changeMetric(value: ScoreDistributionMetric) {
    setMetric(value);
    setCriteriaId(undefined);
    setSelectedBinIndex(undefined);
  }

  const effectiveRoundId = roundId ?? data?.selectedRoundId;
  const effectiveCriteriaId = criteriaId ?? data?.selectedCriteriaId;
  const stats = data?.statistics;
  const averageAndMedianOverlap = stats?.average != null && stats.median != null
    && Math.abs(stats.average - stats.median) < 0.1;
  const statItems = stats ? [
    { label: 'Samples', value: String(stats.sampleCount) },
    { label: 'Teams', value: String(stats.teamCount) },
    { label: 'Judges', value: metric === 'SUBMISSION_RESULT' ? '—' : String(stats.judgeCount) },
    { label: 'Average', value: fixed(stats.average) },
    { label: 'Median', value: fixed(stats.median) },
    { label: 'Std. dev.', value: fixed(stats.standardDeviation) },
    { label: 'Min–Max', value: stats.minimum == null ? '—' : `${fixed(stats.minimum, 1)}–${fixed(stats.maximum, 1)}` },
    { label: '< 50', value: `${stats.belowFiftyCount} · ${fixed(stats.belowFiftyPercentage, 1)}%` },
    { label: '≥ 80', value: `${stats.atOrAboveEightyCount} · ${fixed(stats.atOrAboveEightyPercentage, 1)}%` },
  ] : [];

  return (
    <PixelCard glow gradient style={{ padding: 20 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 800, letterSpacing: '0.06em' }}>
            SCORE DISTRIBUTION
          </div>
          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 4 }}>
            Normalized to 0–100 · click a bar to inspect its assessments
          </div>
        </div>
        {loading && data && (
          <span style={{ color: C.cyan, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>
            REFRESHING...
          </span>
        )}
      </div>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 18 }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: '0.08em' }}>ROUND</span>
          <select
            value={effectiveRoundId ?? ''}
            onChange={event => changeRound(event.target.value)}
            disabled={!data?.rounds.length}
            style={FILTER_STYLE}
          >
            {(data?.rounds ?? []).map(round => (
              <option key={round.roundId} value={round.roundId}>
                {round.name}{round.isFinal ? ' (Final)' : ''}
              </option>
            ))}
          </select>
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: '0.08em' }}>TRACK</span>
          <select
            value={trackId ?? ''}
            onChange={event => { setTrackId(event.target.value ? Number(event.target.value) : undefined); setSelectedBinIndex(undefined); }}
            style={FILTER_STYLE}
          >
            <option value="">All tracks</option>
            {(data?.tracks ?? []).map(track => (
              <option key={track.trackId} value={track.trackId}>{track.name}</option>
            ))}
          </select>
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: '0.08em' }}>METRIC</span>
          <select
            value={metric}
            onChange={event => changeMetric(event.target.value as ScoreDistributionMetric)}
            style={FILTER_STYLE}
          >
            {METRICS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
        </label>

        {metric === 'CRITERIA_SCORE' && (
          <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: '0.08em' }}>CRITERION</span>
            <select
              value={effectiveCriteriaId ?? ''}
              onChange={event => { setCriteriaId(Number(event.target.value)); setSelectedBinIndex(undefined); }}
              disabled={!data?.criteria.length}
              style={FILTER_STYLE}
            >
              {(data?.criteria ?? []).map(criteria => (
                <option key={criteria.criteriaId} value={criteria.criteriaId}>{criteria.name}</option>
              ))}
            </select>
          </label>
        )}
      </div>

      {error && (
        <div style={{ marginTop: 14, padding: '10px 12px', color: C.red, border: '1px solid rgba(239,68,68,0.35)', background: 'rgba(239,68,68,0.06)', fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>
          {error}
        </div>
      )}

      <div style={{ marginTop: 18, border: `1px solid ${C.border}`, background: C.surface2, minHeight: 360, overflowX: 'auto' }}>
        {loading && !data ? (
          <div style={{ height: 360, display: 'grid', placeItems: 'center', color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
            LOADING SCORE DISTRIBUTION...
          </div>
        ) : data && stats?.sampleCount ? (
          <div style={{ minWidth: 720, height: 360, padding: '18px 10px 8px', position: 'relative' }}>
            <div style={{ position: 'absolute', zIndex: 2, top: 8, right: 18, display: 'flex', gap: 8, pointerEvents: 'none' }}>
              {stats.average != null && (
                <span style={{ padding: '3px 7px', color: C.cyan, background: 'rgba(34,211,238,0.08)', border: '1px solid rgba(34,211,238,0.3)', fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}>
                  AVG {fixed(stats.average, 1)}
                </span>
              )}
              {stats.median != null && (
                <span style={{ padding: '3px 7px', color: C.yellow, background: 'rgba(250,204,21,0.08)', border: '1px solid rgba(250,204,21,0.3)', fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}>
                  MED {fixed(stats.median, 1)}
                </span>
              )}
            </div>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.bins} margin={{ top: 24, right: 28, left: 8, bottom: 16 }}>
                <CartesianGrid stroke={C.border} strokeDasharray="3 5" vertical={false} />
                <XAxis
                  dataKey="midpoint"
                  type="number"
                  domain={[0, 100]}
                  ticks={data.bins.map(bin => bin.midpoint)}
                  tickFormatter={value => data.bins.find(bin => bin.midpoint === value)?.label ?? String(value)}
                  tick={{ fill: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}
                  axisLine={{ stroke: C.borderBright }}
                  tickLine={{ stroke: C.border }}
                />
                <YAxis
                  allowDecimals={false}
                  tick={{ fill: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}
                  axisLine={{ stroke: C.borderBright }}
                  tickLine={{ stroke: C.border }}
                  width={36}
                />
                <Tooltip
                  cursor={{ fill: 'rgba(34,197,94,0.05)' }}
                  contentStyle={{ background: C.surface, border: `1px solid ${C.green}`, borderRadius: 0, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}
                  labelStyle={{ color: C.green, marginBottom: 4 }}
                  labelFormatter={value => `${data.bins.find(bin => bin.midpoint === Number(value))?.label ?? value} points`}
                  formatter={value => [`${value} sample${Number(value) === 1 ? '' : 's'}`, 'Frequency']}
                />
                {stats.average != null && (
                  <ReferenceLine
                    x={stats.average}
                    stroke={C.cyan}
                    strokeDasharray="5 4"
                  />
                )}
                {stats.median != null && !averageAndMedianOverlap && (
                  <ReferenceLine
                    x={stats.median}
                    stroke={C.yellow}
                    strokeDasharray="2 4"
                  />
                )}
                <Bar
                  dataKey="count"
                  name="Frequency"
                  maxBarSize={72}
                  onClick={(_entry, index) => setSelectedBinIndex(index)}
                  style={{ cursor: 'pointer' }}
                >
                  {data.bins.map((bin, index) => (
                    <Cell
                      key={bin.label}
                      fill={selectedBinIndex === index ? C.cyan : C.blue}
                      stroke={selectedBinIndex === index ? C.cyanBright : C.blueBright}
                      strokeWidth={selectedBinIndex === index ? 2 : 1}
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        ) : (
          <div style={{ height: 360, display: 'grid', placeItems: 'center', textAlign: 'center', color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: 24 }}>
            No finalized scores match these filters.
          </div>
        )}
      </div>

      {data && (
        <div style={{ marginTop: 12, overflowX: 'auto' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(9, minmax(110px, 1fr))', gap: 10, minWidth: 1080 }}>
            {statItems.map((item, index) => {
              const accent = STAT_ACCENTS[index % STAT_ACCENTS.length];
              return (
                <div
                  key={item.label}
                  style={{
                    minWidth: 0,
                    padding: '13px 12px',
                    background: `linear-gradient(145deg, ${accent.soft}, ${C.surface2} 72%)`,
                    border: `1px solid ${accent.border}`,
                    borderBottom: `3px solid ${accent.main}`,
                    boxShadow: `inset 0 0 22px ${accent.soft}`,
                  }}
                >
                  <div style={{ color: accent.main, fontFamily: "'JetBrains Mono', monospace", fontSize: 18, fontWeight: 800, whiteSpace: 'nowrap', textShadow: `0 0 14px ${accent.soft}` }}>
                    {item.value}
                  </div>
                  <div style={{ color: accent.main, opacity: 0.78, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, marginTop: 5, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    {item.label}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {selectedBin && (
        <div style={{ marginTop: 16, borderTop: `1px solid ${C.border}`, paddingTop: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginBottom: 10 }}>
            <div>
              <span style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 800 }}>
                SELECTED RANGE: {selectedBin.label}
              </span>
              <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginLeft: 10 }}>
                {selectedObservations.length} sample{selectedObservations.length === 1 ? '' : 's'}
              </span>
            </div>
            <button
              type="button"
              onClick={() => setSelectedBinIndex(undefined)}
              style={{ background: 'transparent', border: `1px solid ${C.border}`, color: C.textMuted, padding: '6px 10px', cursor: 'pointer', fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}
            >
              CLEAR RANGE
            </button>
          </div>

          {selectedObservations.length === 0 ? (
            <div style={{ padding: 18, background: C.surface2, border: `1px solid ${C.border}`, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>
              No samples in this score range.
            </div>
          ) : (
            <div style={{ overflowX: 'auto', border: `1px solid ${C.border}` }}>
              <table style={{ width: '100%', minWidth: 680, borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: C.surface2 }}>
                    {['Team', 'Track', metric === 'SUBMISSION_RESULT' ? 'Rank' : 'Judge', 'Score', 'Δ Average'].map(label => (
                      <th key={label} style={{ padding: '10px 14px', color: C.green, textAlign: 'left', fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: '0.08em', borderBottom: `1px solid ${C.border}` }}>
                        {label.toUpperCase()}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {selectedObservations.map((observation, index) => (
                    <tr key={observation.observationId} style={{ background: index % 2 === 0 ? C.surface : C.surface2, borderBottom: `1px solid ${C.border}` }}>
                      <td style={cellStyle}>{observation.teamName}</td>
                      <td style={{ ...cellStyle, color: C.textMuted }}>{observation.trackName ?? '—'}</td>
                      <td style={cellStyle}>
                        {metric === 'SUBMISSION_RESULT'
                          ? (observation.rankPosition ? `#${observation.rankPosition}` : '—')
                          : (observation.judgeName ?? '—')}
                      </td>
                      <td style={{ ...cellStyle, color: C.cyan, fontWeight: 800 }}>{fixed(observation.score)}</td>
                      <td style={{ ...cellStyle, color: (observation.differenceFromAverage ?? 0) >= 0 ? C.green : C.red }}>
                        {observation.differenceFromAverage == null
                          ? '—'
                          : `${observation.differenceFromAverage >= 0 ? '+' : ''}${fixed(observation.differenceFromAverage)}`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </PixelCard>
  );
}
