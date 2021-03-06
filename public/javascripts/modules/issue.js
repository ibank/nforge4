nforge.namespace('issue');

// Compute a color contrasted with the given color.
// e.g.) dimgray if yellow is given.
var contrasted_color = function(color) {
  var rgb = new RGBColor(color);
  // Compute lightness. See http://en.wikipedia.org/wiki/Luma_(video)
  var y709 = rgb.r * 0.21 + 0.72 * rgb.g + rgb.b * 0.07;
  return y709 > 192 ? 'dimgray' : 'white';
};

nforge.issue.label = function () {
  var that;

  that = {
    init: function(urlToLabels, urlToPost, options, callback) {
      that.urlToLabels = urlToLabels;
      that.urlToPost = urlToPost;
      that.options = {
        editable: false
      };
      for(var key in options) {
        that.options[key] = options[key];
      }

      if (that.options.editable) {
        that.addLabelEditor();
      }
      that.setEvent();
      that.updateLabels(callback);
    },

    addLabelEditor: function() {
      var div = $('<div>')
        .addClass('control-group')
        .addClass('label-editor');

      var label = $('<label id="custom-label-label">')
        .addClass('control-label')
        .text(Messages('label.new'));

      var controls = $('<div id="custom-label">')
        .addClass('controls');

      /*
      colors = ['gray', 'red', 'orange', 'yellow', 'green',
        'CornflowerBlue', 'blue', 'purple', 'white'];
      */

      colors = ['#999999', '#da5454', '#ff9933', '#ffcc33', '#99ca3c',
        '#22b4b9', '#4d68b1', '#9966cc', '#ffffff'];

      for (var i = 0; i < colors.length; i++) {
        var button = $('<button type="button">')
          .addClass('issue-label')
          .css('background-color', colors[i]);
        controls.append(button);
      }

      var input_color = $('<input id="custom-label-color" type="text">')
        .addClass('input-small')
        .attr('placeholder', Messages('label.customColor'));

      var input_category = $('<input id="custom-label-category" type="text">')
        .addClass('input-small')
        .attr('data-provider', 'typeahead')
        .attr('placeholder', Messages('label.category'))
        .attr('autocomplete', 'off');

      var input_name = $('<input id="custom-label-name" type="text">')
        .addClass('input-small')
        .attr('placeholder', Messages('label.name'));

      var input_submit = $('<button id="custom-label-submit" type="button">')
        .addClass('btn')
        .text(Messages('label.add'));

      controls.append(input_color);
      controls.append(input_category);
      controls.append(input_name);
      controls.append(input_submit);

      div.append(label);
      div.append(controls);
      $('fieldset.labels').append(div);
    },

    updateLabels: function(callback) {
      var form = $('<form>')
        .attr('method', 'get')
        .attr('action', that.urlToLabels);

      form.ajaxForm({
        success: function(responseBody, statusText, xhr) {
          var labels = responseBody;

          if (!(labels instanceof Object)) {
            alert('Failed to update - Server error.');
            return;
          }

          labels = labels.sort(function(a, b) {
            if (a.category == b.category) {
              return a.name > b.name;
            } else {
              return a.category > b.category;
            }
          });

          for (var i = 0; i < labels.length; i++) {
            that.add_label_into_category(labels[i].id, labels[i].category, labels[i].name, labels[i].color);
          }

          if (callback) callback();
        }
      });

      form.submit();
    },

    updateSelectedColor: function(color) {
      // Change the name input area's color to the selected color.
      contrasted = contrasted_color(color);
      $('#custom-label-name').css('background-color', color);
      $('#custom-label-name').css('color', contrasted);
      selectors = ['#custom-label-name:-moz-placeholder',
        '#custom-label-name:-ms-input-placeholder',
        '#custom-label-name::-webkit-input-placeholder'];
      for (var i in selectors) {
        try {
          document.styleSheets[0].addRule(selectors[i], 'color: ' + contrasted + ' !important');
          document.styleSheets[0].addRule(selectors[i], 'opacity: 0.8');
        } catch (e) {
          continue;
        }
      }
    },

    setEvent: function() {
      $('#custom-label button.issue-label').click(function(e) {
        var color, contrasted, selectors;
        var target = $(e.target || e.srcElement || e.originalTarget);

        // Set clicked button active.
        $('#custom-label button.issue-label').removeClass('active');
        target.addClass('active');

        // Get the selected color.
        color = target.css('background-color');

        // Fill the color input area with the hexadecimal value of
        // the selected color.
        $('#custom-label-color').val(new RGBColor(color).toHex());

        that.updateSelectedColor(color);

        // Focus to the category input area.
        $('#custom-label-category').focus();
      });

      var issueForm = $('form#issue-form,form.form-search');
      issueForm.submit(function() {
        var buttons = $('fieldset.labels div[category] button.active[labelId]');
        for (var i = 0; i < buttons.length; i++) {
          issueForm.append(
            '<input type="hidden" name="labelIds" value="' + $(buttons[i]).attr('labelId') + '">');
        }

        return true;
      });

      $('#custom-label-submit').click(function(e) {
        that.add_custom_label();
      });

      $('#custom-label input').keypress(function(e) {
        if (e.keyCode == 13) {
          return false;
        }
      });

      $('#custom-label input').keyup(function(e) {
        if (e.keyCode == 13) {
          that.add_custom_label();
        }
      });

      $('#custom-label-color').keyup(function(e) {
        var target = $(e.target || e.srcElement || e.originalTarget);
        var color = target.val();
        if (new RGBColor(color).ok) {
          that.updateSelectedColor(color);
        }
      });
    },

    removeLabel: function(id) {
      var label, category, source;

      label = $('[labelId=' + id + ']');
      if (label.siblings().size() > 0) {
        label.remove();
      } else {
        category = $(label.parents('div').get(0)).attr('category');
        $('[category="' + category + '"]').parent().remove();
        source = $('#custom-label-category').typeahead().data('typeahead').source;
        source.pop(source.indexOf(category));
      }
    },

    add_del_link: function(button, id, color) {
      var delLink = $('<a>')
        .addClass('icon-trash')
        .addClass('del-link')
        .addClass('active-' + contrasted_color(color))

      delLink.click(function() {
        var form = $('<form>')
        form.attr('action', that.urlToLabels + '/' + id);
        form.attr('method', 'post');
        form.append('<input type="hidden" name="_method" value="delete">');
        form.ajaxForm({
          success: function(responseBody, statusText, xhr) {
            that.removeLabel(id);
          }, error: function() {
          }
        });
        form.submit();
      });

      button.append(delLink);
    },

    add_label_into_category: function(id, category, name, color) {
      var elem = $('fieldset.labels div[category="' + category + '"]')
      var button = $('<button type="button">' + name + '</button>')
        .addClass('btn')
        .attr('labelId', id);
      if (that.options.editable) {
        that.add_del_link(button, id, color);
      }
      document.styleSheets[0].addRule('.labels button.btn.active[labelId="' + id + '"]', 'background-color: ' + color);
      button.addClass('active-' + contrasted_color(color));
      if (elem.length > 0) {
        elem.append(button);
      } else {
        var div = $('<div>').addClass('control-group');
        var label = $('<label class="control-label" category="' + category + '">' + category + '</label>');
        var controls = $('<div class="controls" data-toggle="buttons-checkbox" category="' + category + '">');
        div.append(label);
        div.append(controls);
        labelEditor = $('.label-editor');
        if (labelEditor.length > 0) {
            $('.label-editor').before(div);
        } else {
            $('.labels').append(div);
        }

        // Edit Button
        /*
        var editButton = $('<button type="button">Edit</button>')
            .css('float', 'right')
            .prepend('<span class="icon-edit"></span>')
            .click(function() {
              that.enterEditMode(category);
            });
        controls.append(editButton);
        */
        controls.append(button);
        if (that.options.editable) {
          $('#custom-label-category').typeahead().data('typeahead').source.push(category);
        }
      }

      return button;
    },

    /*
    enterEditMode: function(category) {
      $('[category="' + category + '"] [labelId] .del-link').css('display', 'inline');
    },
    */

    add_custom_label: function() {
      var form = $('<form>')
        .attr('method', 'post')
        .attr('enctype', 'multipart/form-data')
        .attr('action', that.urlToPost);

      var category = $('#custom-label-category').val();
      var name = $('#custom-label-name').val();
      var color = $('#custom-label-color').val();
      form.append('<input type="hidden" name="category" value="' + category + '">');
      form.append('<input type="hidden" name="name" value="' + name + '">');
      form.append('<input type="hidden" name="color" value="' + color + '">');

      form.ajaxForm({
        success: function(responseBody, statusText, xhr) {
          label = responseBody;
          if (!(label instanceof Object)) {
              notification.text('Failed to add custom label - Server error.');
              return;
          }
          var label = that.add_label_into_category(label.id, label.category, label.name, label.color);
          // label.addClass('active');
        },
        error: function() {
        }
      });

      form.submit();
    }
  }

  return that;
};

nforge.issue.view = function () {
  var that;

  that = {
    init: function(filesUrl) {
      var labels, label, attachments;

      labels = $('.issue-label');
      for (var i = 0; i < labels.length; i++) {
        label = $(labels[i]);
        label.css('color', contrasted_color(label.css('background-color')));
      }

      fileUploader($('#upload'), $('#comment-editor'), filesUrl);
      attachments = $('.attachments');
      for (var i = 0; i < attachments.length; i++) {
        fileDownloader($(attachments[i]), filesUrl);
      }
    }
  }

  return that;
};

nforge.issue.list = function() {
  var that;
that = {
    init: function() {
      var searchForm = $('form.form-search');

      $('#advanced-search-form').css('display', 'none');
      $('#advanced-search').click(function(e) {
        var target = $(e.target || e.srcElement || e.originalTarget);
        if (target.hasClass('active')) {
          $('#advanced-search-form').css('display', 'none');
        } else {
          $('#advanced-search-form').css('display', '');
        }
      });

      $('.properties div.controls button').click(function(e) {
        var target = $(e.target || e.srcElement || e.originalTarget);
        if (target.hasClass('active')) {
          target.removeClass('active');
          return false;
        }
      });

      searchForm.submit(function() {
        var assigneeId = $('fieldset.properties button.active[assigneeId]')
          .attr('assigneeId');
        var milestoneId = $('fieldset.properties button.active[milestoneId]')
          .attr('milestoneId');

        if (assigneeId !== undefined) {
          searchForm.append(
            '<input type="hidden" name="assigneeId" value="' + assigneeId + '">');
        }

        if (milestoneId !== undefined) {
          searchForm.append(
            '<input type="hidden" name="milestone" value="' + milestoneId + '">');
        }
      });
    }
  }

  return that;
}

nforge.issue.new = function() {
  var that;

  that = {
    init: function(filesUrl) {
      fileUploader($('#upload'), $('#body'), filesUrl);
    }
  }

  return that;
};

nforge.issue.edit = nforge.issue.new;
