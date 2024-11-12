var jobTagToIdMap = {};
var jobTagsInput;

function updateTagsTotal()
{
    var totalTags = Object.keys(jobTagToIdMap).length;
    $("#tagHeaderTotal").empty().append("(" +  totalTags + ")");
    updateJobName();
}

function addTag(tag)
{
    var queryString = "?tagText="+tag;
    $.ajax({
        type: "POST",
        url: "/gp/rest/v1/jobs/" + currentJobNumber +"/tags/add"+ queryString,
        cache: false,
        success: function (response) {

            console.log(response);
            console.log("job tag id: " + response.jobTagId);


            if(response != undefined && response != null && response.success
                && response.jobTagId != undefined && response.jobTagId != null)
            {
                jobTagToIdMap[tag] = response.jobTagId;
                updateTagsTotal();
            }
            else
            {
                alert("An error occurred while adding tag " + tag);
                console.log("An error occurred while adding tag " + tag);
                $('#statusJobTags').removeTag(tag);
            }
        },
        dataType: "json",
        error: function (xhr, ajaxOptions, thrownError) {
            console.log("Response from server: status=" + xhr.status + " text=" + xhr.responseText);
            console.log(thrownError);
        }
    });
}

function deleteTag(tag)
{
    if( jobTagToIdMap[tag] == undefined || jobTagToIdMap[tag] == null)
    {
        //do nothing since id for tag could not be found
        return;
    }

    var jobTagId = jobTagToIdMap[tag];

    var queryString = "?jobTagId="+jobTagId;
    $.ajax({
        type: "POST",
        url: "/gp/rest/v1/jobs/" + currentJobNumber +"/tags/delete"+ queryString,
        cache: false,
        success: function (response) {

            console.log(response);

            if(response.success)
            {
                delete jobTagToIdMap[tag];
                updateTagsTotal();
            }
            else
            {
                alert("An error occurred while deleting " + tag);
                console.log("An error occurred while deleting tag " + tag);
                var tagValues = Object.keys(jobTagToIdMap);
                tagValues.sort();

                jobTagsInput.importTags(tagValues.join(","));
            }
        },
        dataType: "json",
        error: function (xhr, ajaxOptions, thrownError) {
            console.log("Response from server: status=" + xhr.status + " text=" + xhr.responseText);
            console.log(thrownError);
        }
    });
}

$(function() {
    jobTagsInput = $('#statusJobTags').tagsInput(
    {
        defaultText:'Add tag and press enter...',
        width: '98%',
        height: '40px',
        interactive: true,
        placeholderColor: '#CCC',
        onAddTag: addTag,
        onRemoveTag: deleteTag,
        autocomplete_url: '/gp/rest/v1/tags/',
        autocomplete:{
            minLength: 0,
            response: tagResponse
        },
        maxChars: 511
    });

    //import the tags
    $.ajax({
        type: "GET",
        url: "/gp/rest/v1/jobs/" + currentJobNumber +"/tags",
        cache: false,
        success: function (response) {

            console.log(response);
            if(response != null && response.tags != undefined && response.tags != null)
            {
                var focusIn = $.cookie("show_tags_focus"+currentJobNumber);

                if(response.tags == undefined || response.tags == null
                    || response.tags.length == 0)
                {
                    console.log("Error getting tags: " + response.tags);
                }

                for(var i=0; i < response.tags.length;i++)
                {
                    var tag = response.tags[i].tag;

                    if(tag != undefined && tag != null)
                    {
                        jobTagToIdMap[tag.tag] = response.tags[i].id;
                    }

                }
                var tagValues = Object.keys(jobTagToIdMap);
                tagValues.sort();

                jobTagsInput.importTags(tagValues.join(","));
                updateTagsTotal();

               var tag = $.cookie("show_tags_value"+currentJobNumber);
                if(tag)
                {
                    $("#tagsContent").find("input").last().val(tag);
                }

                if(focusIn)
                {
                    $("#tagsContent").find("input").last().focus();
                }
                updateJobName(response.tags)
            }
        },
        dataType: "json",
        error: function (xhr, ajaxOptions, thrownError) {
            console.log("Response from server: status=" + xhr.status + " text=" + xhr.responseText);
            console.log(thrownError);
        }
    });

    $("#tagsHeader").click(function () {
        $(this).next().toggle();

        var toggleImg = $(this).find(".sectionToggle");

        if (toggleImg == null) {
            //toggle image not found
            // just log error and return
            console.log("Could not find toggle image for hiding and showing parameter groups sections");

            return;
        }

        //change the toggle image to indicate hide or show
        var imageSrc = toggleImg.attr("src");
        if (imageSrc.indexOf('collapse') != -1)
        {
            imageSrc = imageSrc.replace("collapse", "expand");
            $.removeCookie("show_tags_"+currentJobNumber);
        }
        else
        {
            imageSrc = imageSrc.replace("expand", "collapse");
            $.cookie("show_tags_"+currentJobNumber, true);

        }

        toggleImg.attr("src", imageSrc);
    });


    if($.cookie("show_tags_"+currentJobNumber) == null)
    {
        $("#tagsHeader").click();
    }

    //keep track of changes to the tag text area
    $("#statusJobTags_tag").keyup(function()
    {
        $.cookie("show_tags_value"+currentJobNumber, $(this).val());
    });

    //keep track of focus events in the tag text area
    $("#tagsContent").find("input").last().keyup(function()
    {
        var value = $(this).val();

        $(this).val(value.toLowerCase());
    });

    //keep track of focus events in the tag text area
    $("#tagsContent").find("input").last().focus(function()
    {
        $.cookie("show_tags_focus"+currentJobNumber, true);
    });

    //keep track of blur events in the tag text area
    $("#tagsContent").find("input").last().blur(function()
    {
        $.removeCookie("show_tags_focus"+currentJobNumber);
    });
});


 
function updateJobName() {
    // Initialize the job link
    var jobLink = $(".jobresult-job");
    // Cache the old value
    var oldValue = jobLink.text();

    // Update the job name if a tag starting with name= or jobname= is found
    var taskName = "";
    for (var tag in jobTagToIdMap) {
        if (tag.toLowerCase().startsWith("name=") || tag.toLowerCase().startsWith("jobname=")) {
            taskName = tag.split("=")[1];
            break;
        }
    }
   
    // Find the parent TR element of the jobLink
    var parentTR = jobLink.closest("tr");
    var existingTR = parentTR.next(".cached-job-name");

 	if (taskName != "") {
    	  jobLink.text(taskName);
		  if (existingTR.length == 0) {
			    var newTR = $("<tr class='cached-job-name'></tr>");
			    var newTD = $("<td style='padding-left:24px; font-weight:bold;'></td>").attr("colspan", parentTR.children("td").length).text(oldValue);
			    newTR.append(newTD);	
			    // Insert the new TR immediately below the parent TR
			    parentTR.after(newTR);
		  }
    } else {
		jobLink.text(jobLink.attr("data-taskname"));
		if (existingTR.length > 0) {
            existingTR.remove();
        }
       
    }

	// update the recent jobs list so it is also correct
    initRecentJobs();
   
}


