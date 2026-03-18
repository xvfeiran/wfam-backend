UPDATE APMS_RETURN_ORDER
SET STATUS = 'submitted'
WHERE STATUS IN ('in_initial_analysis','in_detailed_analysis',
                 'pending_approval','analysis_completed',
                 'scrap_in_progress','scrapped');
COMMIT;
