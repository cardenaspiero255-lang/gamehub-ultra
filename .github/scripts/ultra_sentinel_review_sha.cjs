'use strict';
/** Validate the SHA in provider report metadata, not arbitrary PR content. */
function matchesReviewHead(body,sha){
 if(typeof body!=='string'||!/^[a-f0-9]{12}$/i.test(sha))return false;
 const normalized=body.replace(/\\`/g,'').replace(/`/g,'');
 return new RegExp('(?:^|[^a-zA-Z0-9])head\\s+'+sha+'(?![a-f0-9])','i').test(normalized);
}
module.exports={matchesReviewHead};
