import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { invitesApi, HackathonEvent, participationRequestsApi, ApiError, apiErrorMessage } from "@/shared/apiClient";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { NoTeamDashboard } from "./participant/screens/NoTeamDashboard";
import { ExistingTeamDashboard } from "./participant/screens/ExistingTeamDashboard";
import { CreateTeamScreen } from "./participant/screens/CreateTeamScreen";
import { SuccessScreen } from "./participant/screens/SuccessScreen";
import { EventDetailDrawer } from "./participant/components/EventDetailDrawer";
import { InvitationsDrawer } from "./participant/components/InvitationsDrawer";

type Screen = 'dashboard' | 'create' | 'success';

export function ParticipantDashboard() {
    const navigate = useNavigate();
    const { currentUser } = useAuth();
    const { addToast } = useNotifications();

    const [screen, setScreen] = useState<Screen>('dashboard');
    const [drawerEvent, setDrawerEvent] = useState<HackathonEvent | null>(null);
    const [createEventId, setCreateEventId] = useState<number | null>(null);
    const [createTrackId, setCreateTrackId] = useState<number | null>(null);
    const [pendingTeamName, setPendingTeamName] = useState<string | null>(null);
    const [showInvites, setShowInvites] = useState(false);
    const [pendingInviteCount, setPendingInviteCount] = useState(0);
    // Request-to-compete flow for a participant who finished their last event and
    // is now inactive (not in any running competition).
    const [requestingActive, setRequestingActive] = useState(false);
    const [activeRequested, setActiveRequested] = useState(false);
    const [confirmActive, setConfirmActive] = useState(false);

    const loadInviteCount = useCallback(() => {
        invitesApi.getPending().then(r => setPendingInviteCount((r.data ?? []).length)).catch(() => setPendingInviteCount(0));
    }, []);

    useEffect(() => { loadInviteCount(); }, [loadInviteCount]);

    if (!currentUser) return null;

    const inactive = !currentUser.is_active;

    async function submitRequestActive() {
        if (requestingActive || activeRequested) return;
        setRequestingActive(true);
        try {
            await participationRequestsApi.request();
            setActiveRequested(true);
            setConfirmActive(false);
            addToast({ type: "success", title: "Request sent", message: "A coordinator will review your request to compete this season." });
        } catch (err) {
            // A 400 usually means a request is already pending — treat as sent.
            if (err instanceof ApiError && err.status === 400) { setActiveRequested(true); setConfirmActive(false); }
            addToast({ type: "warning", title: "Request failed", message: apiErrorMessage(err, "Failed to send request.") });
        } finally {
            setRequestingActive(false);
        }
    }

    // Just-created team → show success first, even though team context is now set.
    if (screen === 'success' && pendingTeamName) {
        return (
            <SuccessScreen
                teamName={pendingTeamName}
                onDashboard={() => { setPendingTeamName(null); setScreen('dashboard'); }}
                onViewTeam={() => navigate('/team/view')}
            />
        );
    }

    // Participant WITH a team → member / leader console. A dormant team (past
    // season, no live TeamEventEntry) still resolves team_id !== null, so the
    // reactivate-account flow must stay reachable here too, not just below.
    if (currentUser.team_id !== null) {
        return (
            <div style={{ position: "relative" }}>
                <ExistingTeamDashboard
                    inactive={inactive}
                    requestingActive={requestingActive}
                    activeRequested={activeRequested}
                    onRequestActive={() => setConfirmActive(true)}
                />

                {confirmActive && (
                    <ConfirmDialog
                        title="Request to compete this season?"
                        message="You finished your last event, so your account is currently inactive. Send a request for a coordinator to add you to the current competition."
                        confirmLabel="SEND REQUEST"
                        working={requestingActive}
                        onConfirm={submitRequestActive}
                        onClose={() => { if (!requestingActive) setConfirmActive(false); }}
                    />
                )}
            </div>
        );
    }

    // Create-team form.
    if (screen === 'create') {
        return (
            <CreateTeamScreen
                initialEventId={createEventId}
                initialTrackId={createTrackId}
                onBack={() => { setScreen('dashboard'); setDrawerEvent(null); }}
                onSubmit={(teamName) => {
                    setPendingTeamName(teamName);
                    setDrawerEvent(null);
                    setScreen('success');
                    addToast({ type: "success", title: "Team Created!", message: `"${teamName}" is pending coordinator approval.` });
                }}
            />
        );
    }

    // Inactive participants request to compete instead of registering directly.
    function startRegisterOrRequest(eventId?: number, trackId?: number) {
        if (inactive) { setConfirmActive(true); return; }
        setCreateEventId(eventId ?? null);
        setCreateTrackId(trackId ?? null);
        setDrawerEvent(null);
        setScreen('create');
    }

    // No-team dashboard.
    return (
        <div style={{ position: "relative" }}>
            <NoTeamDashboard
                pendingTeamName={pendingTeamName}
                pendingInviteCount={pendingInviteCount}
                inactive={inactive}
                requestingActive={requestingActive}
                activeRequested={activeRequested}
                onRequestActive={() => setConfirmActive(true)}
                onCreateTeam={startRegisterOrRequest}
                onViewDetails={(ev) => setDrawerEvent(ev)}
                onWaitForInvite={() => setShowInvites(true)}
            />

            {showInvites && (
                <InvitationsDrawer onClose={() => { setShowInvites(false); loadInviteCount(); }} />
            )}

            {drawerEvent && (
                <EventDetailDrawer
                    event={drawerEvent}
                    onClose={() => setDrawerEvent(null)}
                    onCreateTeam={(eventId) => startRegisterOrRequest(eventId)}
                />
            )}

            {confirmActive && (
                <ConfirmDialog
                    title="Request to compete this season?"
                    message="You finished your last event, so your account is currently inactive. Send a request for a coordinator to add you to the current competition."
                    confirmLabel="SEND REQUEST"
                    working={requestingActive}
                    onConfirm={submitRequestActive}
                    onClose={() => { if (!requestingActive) setConfirmActive(false); }}
                />
            )}
        </div>
    );
}
