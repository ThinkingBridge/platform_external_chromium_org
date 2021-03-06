// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file performs actions on media elements.
(function() {
  function playMedia(selector) {
    // Performs the "Play" action on media satisfying selector.
    var mediaElements = window.__findMediaElements(selector);
    for (var i = 0; i < mediaElements.length; i++) {
      console.log('Playing element: ' + mediaElements[i].src);
      play(mediaElements[i]);
    }
  }

  function play(element) {
    if (element instanceof HTMLMediaElement)
      playHTML5Element(element);
    else
      throw new Error('Can not play non HTML5 media elements.');
  }

  function playHTML5Element(element) {
    function logEventHappened(e) {
      element[e.type + '_completed'] = true;
    }
    function onError(e) {
      throw new Error('Error playing media :' + e.type);
    }
    element.addEventListener('playing', logEventHappened);
    element.addEventListener('ended', logEventHappened);
    element.addEventListener('error', onError);
    element.addEventListener('abort', onError);

    var willPlayEvent = document.createEvent('Event');
    willPlayEvent.initEvent('willPlay', false, false);
    element.dispatchEvent(willPlayEvent);
    element.play();
  }

  window.__playMedia = playMedia;
})();
