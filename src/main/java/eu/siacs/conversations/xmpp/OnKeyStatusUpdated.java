package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.crypto.axolotl.AxolotlService;

public interface OnKeyStatusUpdated {
	public void onKeyStatusUpdated(AxolotlService.FetchStatus report);
}
