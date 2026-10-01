// Ayushflix Smart TV Spatial Navigation Engine (Samsung Tizen Remote & Keyboard)
const SpatialNav = {
    currentFocus: null,

    init: function() {
        window.addEventListener('keydown', (e) => this.handleKeyDown(e));
        
        // Register Tizen TV keys if running on Tizen OS
        if (typeof tizen !== 'undefined' && tizen.tvinputdevice) {
            try {
                const keys = ['MediaPlay', 'MediaPause', 'MediaPlayPause', 'MediaFastForward', 'MediaRewind', '10009'];
                keys.forEach(k => {
                    try { tizen.tvinputdevice.registerKey(k); } catch (_) {}
                });
            } catch (_) {}
        }

        // Set initial focus
        setTimeout(() => {
            const first = document.querySelector('.focusable');
            if (first) this.focus(first);
        }, 300);
    },

    focus: function(el) {
        if (!el) return;
        if (this.currentFocus) {
            this.currentFocus.classList.remove('focused');
            this.currentFocus.blur();
        }
        this.currentFocus = el;
        el.classList.add('focused');
        el.focus();
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
    },

    handleKeyDown: function(e) {
        const keyCode = e.keyCode || e.which;

        // Player overlay has its own key handling
        if (window.Player && window.Player.isOpen()) {
            window.Player.handleKey(keyCode, e);
            return;
        }

        // Modal overlay handling
        const modal = document.getElementById('details-modal');
        const isModalOpen = modal && modal.style.display === 'flex';

        switch (keyCode) {
            case 37: // Left
                this.navigate('left', isModalOpen);
                e.preventDefault();
                break;
            case 38: // Up
                this.navigate('up', isModalOpen);
                e.preventDefault();
                break;
            case 39: // Right
                this.navigate('right', isModalOpen);
                e.preventDefault();
                break;
            case 40: // Down
                this.navigate('down', isModalOpen);
                e.preventDefault();
                break;
            case 13: // Enter / OK
                if (this.currentFocus) this.currentFocus.click();
                e.preventDefault();
                break;
            case 10009: // Tizen Return / Back key
            case 27:    // Escape
            case 8:     // Backspace
                if (isModalOpen) {
                    window.App.closeModal();
                    e.preventDefault();
                } else if (window.App.isSearchOpen()) {
                    window.App.showHome();
                    e.preventDefault();
                } else if (typeof tizen !== 'undefined') {
                    try { tizen.application.getCurrentApplication().exit(); } catch (_) {}
                }
                break;
        }
    },

    navigate: function(direction, isModalOpen) {
        const selector = isModalOpen ? '#details-modal .focusable' : 'body > *:not(#details-modal) .focusable';
        const focusables = Array.from(document.querySelectorAll(selector)).filter(el => {
            return el.offsetParent !== null && !el.disabled;
        });

        if (!this.currentFocus || !focusables.includes(this.currentFocus)) {
            if (focusables.length) this.focus(focusables[0]);
            return;
        }

        const currentRect = this.currentFocus.getBoundingClientRect();
        let bestTarget = null;
        let bestDistance = Infinity;

        focusables.forEach(el => {
            if (el === this.currentFocus) return;
            const r = el.getBoundingClientRect();

            let isCandidate = false;
            let primaryDist = 0;
            let secondaryDist = 0;

            if (direction === 'left' && r.right <= currentRect.left + 5) {
                isCandidate = true;
                primaryDist = currentRect.left - r.right;
                secondaryDist = Math.abs((currentRect.top + currentRect.height/2) - (r.top + r.height/2));
            } else if (direction === 'right' && r.left >= currentRect.right - 5) {
                isCandidate = true;
                primaryDist = r.left - currentRect.right;
                secondaryDist = Math.abs((currentRect.top + currentRect.height/2) - (r.top + r.height/2));
            } else if (direction === 'up' && r.bottom <= currentRect.top + 5) {
                isCandidate = true;
                primaryDist = currentRect.top - r.bottom;
                secondaryDist = Math.abs((currentRect.left + currentRect.width/2) - (r.left + r.width/2));
            } else if (direction === 'down' && r.top >= currentRect.bottom - 5) {
                isCandidate = true;
                primaryDist = r.top - currentRect.bottom;
                secondaryDist = Math.abs((currentRect.left + currentRect.width/2) - (r.left + r.width/2));
            }

            if (isCandidate) {
                // Weighted distance formula favoring elements aligned in the primary direction
                const totalDist = primaryDist + secondaryDist * 1.8;
                if (totalDist < bestDistance) {
                    bestDistance = totalDist;
                    bestTarget = el;
                }
            }
        });

        if (bestTarget) {
            this.focus(bestTarget);
        }
    }
};

window.SpatialNav = SpatialNav;
